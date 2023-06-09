package com.cody.roughcode.project.service;

import com.cody.roughcode.alarm.dto.req.AlarmReq;
import com.cody.roughcode.alarm.service.AlarmServiceImpl;
import com.cody.roughcode.code.entity.Codes;
import com.cody.roughcode.code.repository.CodesRepository;
import com.cody.roughcode.email.service.EmailServiceImpl;
import com.cody.roughcode.exception.NotMatchException;
import com.cody.roughcode.exception.NotNewestVersionException;
import com.cody.roughcode.exception.S3FailedException;
import com.cody.roughcode.exception.UpdateFailedException;
import com.cody.roughcode.project.dto.req.FeedbackInsertReq;
import com.cody.roughcode.project.dto.req.FeedbackUpdateReq;
import com.cody.roughcode.project.dto.res.*;
import com.cody.roughcode.project.dto.req.ProjectReq;
import com.cody.roughcode.project.entity.*;
import com.cody.roughcode.project.repository.*;
import com.cody.roughcode.user.entity.Users;
import com.cody.roughcode.user.repository.UsersRepository;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.webrisk.v1.WebRiskServiceClient;
import com.google.cloud.webrisk.v1.WebRiskServiceSettings;
import com.google.webrisk.v1.SearchUrisRequest;
import com.google.webrisk.v1.SearchUrisResponse;
import com.google.webrisk.v1.ThreatType;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;


import javax.mail.MessagingException;
import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectsServiceImpl implements ProjectsService{

    private final S3FileServiceImpl s3FileService;
    private final AlarmServiceImpl alarmService;

    private final UsersRepository usersRepository;
    private final ProjectsRepository projectsRepository;
    private final ProjectsInfoRepository projectsInfoRepository;
    private final ProjectSelectedTagsRepository projectSelectedTagsRepository;
    private final ProjectSelectedTagsQRepository projectSelectedTagsQRepository;
    private final ProjectTagsRepository projectTagsRepository;
    private final CodesRepository codesRepository;
    private final FeedbacksRepository feedbacksRepository;
    private final FeedbacksComplainsRepository feedbacksComplainsRepository;
    private final SelectedFeedbacksRepository selectedFeedbacksRepository;
    private final ProjectFavoritesRepository projectFavoritesRepository;
    private final ProjectFavoritesQRepository projectFavoritesQRepository;
    private final ProjectLikesRepository projectLikesRepository;
    private final FeedbacksLikesRepository feedbacksLikesRepository;
    private final EmailServiceImpl emailService;

    private final EntityManager entityManager;

    @Override
    @Transactional
    public Long insertProject(ProjectReq req, Long usersId) throws MessagingException, IOException {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 등록 전 다시 url 체크
        checkProject(req.getUrl(), false);

        ProjectsInfo info = ProjectsInfo.builder()
                .url(req.getUrl())
                .notice(req.getNotice())
                .content(req.getContent())
                .build();

        // 북마크한 무슨무슨 프로젝트 ver1의 새 버전 ver2 업데이트  -> [“북마크한”, “무슨무슨 프로젝트 ver1의 새 버전 ver2”, “업데이트”]
        List<Long> bookmarkAlarm = new ArrayList<>(); // 알람을 보낼 user Id
        // 작성한 피드백이 반영된 무슨무슨 프로젝트 ver1의 새 버전 ver2 업데이트 -> [“작성한 피드백이 반영된”, “무슨무슨 프로젝트 ver1의 새 버전 ver2”, “업데이트”]
        List<Long> feedbackAlarm = new ArrayList<>(); // 알람을 보낼 user Id

        // 새 프로젝트를 생성하는거면 projectNum은 작성자의 projects_cnt + 1
        // 전의 프로젝트를 업데이트하는거면 projectNum은 전의 projectNum과 동일
        Long projectNum;
        int projectVersion;
        int likeCnt = 0;
        if(req.getProjectId() == -1){ // 새 프로젝트 생성
            user.projectsCntUp();
            usersRepository.save(user);

            projectNum = user.getProjectsCnt();
            projectVersion = 1;
        } else { // 기존 프로젝트 버전 업
            // num 가져오기
            // num과 user가 일치하는 max version값 가져오기
            // num과 user와 max version값에 일치하는 project 가져오기
            Projects original = projectsRepository.findByProjectsIdAndExpireDateIsNull(req.getProjectId());
            if(original == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
            original = projectsRepository.findLatestProject(original.getNum(), user.getUsersId());
            if(original == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

            if(!original.getProjectWriter().equals(user)) throw new NotMatchException();

            projectNum = original.getNum();
            projectVersion = original.getVersion() + 1;
            likeCnt = original.getLikeCnt();

            // 이전 버전 프로젝트 전부 닫기
            List<Projects> oldProjects = projectsRepository.findByNumAndProjectWriterAndExpireDateIsNullOrderByVersionDesc(projectNum, user);
            for (Projects p : oldProjects) {
                p.close(true);
                projectsRepository.save(p);
            }

            // 알람 받을 사람 등록
            List<Users> favoritedUsers = projectFavoritesQRepository.findByProjects(original);
            if(favoritedUsers != null)
                for (Users u : favoritedUsers) {
                    bookmarkAlarm.add(u.getUsersId());
                }
        }

        Projects savedProject = null;
        Long projectId = -1L;
        try {
            Projects project = Projects.builder()
                    .num(projectNum)
                    .version(projectVersion)
                    .img("https://d2swdwg2kwda2j.cloudfront.net/no+image.jpeg")
                    .introduction(req.getIntroduction())
                    .title(req.getTitle())
                    .projectWriter(user)
                    .likeCnt(likeCnt)
                    .build();
            savedProject = projectsRepository.save(project);
            projectId = savedProject.getProjectsId();

            // tag 등록
            if(req.getSelectedTagsId() != null) {
                List<ProjectSelectedTags> selectedTagssList = new ArrayList<>();
                List<ProjectTags> tagsList = new ArrayList<>();
                for (Long id : req.getSelectedTagsId()) {
                    ProjectTags projectTag = projectTagsRepository.findByTagsId(id);
                    selectedTagssList.add(ProjectSelectedTags.builder()
                            .tags(projectTag)
                            .projects(project)
                            .build());

                    projectTag.cntUp();
                    tagsList.add(projectTag);
                }
                projectSelectedTagsRepository.saveAll(selectedTagssList);
                projectTagsRepository.saveAll(tagsList);
            }else log.info("등록한 태그가 없습니다");

            // feedback 선택
            if(req.getSelectedFeedbacksId() != null) {
                List<Feedbacks> feedbacksList = new ArrayList<>();
                List<SelectedFeedbacks> selectedFeedbacksList = new ArrayList<>();
                for (Long id : req.getSelectedFeedbacksId()) {
                    Feedbacks feedback = feedbacksRepository.findByFeedbacksId(id);
                    if (feedback == null) throw new NullPointerException("일치하는 피드백이 없습니다");
                    if (!feedback.getProjectsInfo().getProjects().getNum().equals(projectNum))
                        throw new NullPointerException("피드백과 프로젝트가 일치하지 않습니다");
                    feedback.selectedUp();
                    feedbacksList.add(feedback);

                    SelectedFeedbacks selectedFeedback = SelectedFeedbacks.builder()
                            .feedbacks(feedback)
                            .projects(savedProject)
                            .build();
                    selectedFeedbacksList.add(selectedFeedback);
                    if(feedback.getUsers() != null) feedbackAlarm.add(feedback.getUsers().getUsersId());
                }
                feedbacksRepository.saveAll(feedbacksList);
                selectedFeedbacksRepository.saveAll(selectedFeedbacksList);
            }
            else log.info("선택한 피드백이 없습니다");

            info.setProjects(savedProject);
            projectsInfoRepository.save(info);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new S3FailedException(e.getMessage());
        }

        // 알람들 저장
        // 북마크한 무슨무슨 프로젝트 ver1의 새 버전 ver2 업데이트  -> [“북마크한”, “무슨무슨 프로젝트 ver1의 새 버전 ver2”, “업데이트”]
        for (Long id : bookmarkAlarm) {
            AlarmReq alarmContent = AlarmReq.builder()
                    .section("project")
                    .userId(id)
                    .content(List.of("북마크한 프로젝트, ", savedProject.getTitle() + " ver" + (savedProject.getVersion() - 1) + "의 새 버전 ver" + savedProject.getVersion(), "가 업데이트 되었습니다"))
                    .postId(projectId).build();
            alarmService.insertAlarm(alarmContent);

            emailService.sendAlarm("북마크한 프로젝트가 업데이트되었습니다", alarmContent);
        }
        // 작성한 피드백이 반영된 무슨무슨 프로젝트 ver1의 새 버전 ver2 업데이트 -> [“작성한 피드백이 반영된”, “무슨무슨 프로젝트 ver1의 새 버전 ver2”, “업데이트”]
        for (Long id : feedbackAlarm) {
            AlarmReq alarmContent = AlarmReq.builder()
                    .section("project")
                    .userId(id)
                    .content(List.of("작성한 피드백이 반영된 프로젝트, ", savedProject.getTitle() + " ver" + (savedProject.getVersion() - 1) + "의 새 버전 ver" + savedProject.getVersion(), "가 업데이트 되었습니다."))
                    .postId(projectId).build();
            alarmService.insertAlarm(alarmContent);

            emailService.sendAlarm("피드백이 반영되었습니다", alarmContent);
        }

        return projectId;
    }

    @Override
    @Transactional
    public int updateProjectThumbnail(MultipartFile thumbnail, Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();
        Projects latestProject = projectsRepository.findLatestProject(project.getNum(), usersId);
        if(!project.equals(latestProject)) throw new NotNewestVersionException();

        Long projectNum = project.getNum();
        int projectVersion = project.getVersion();

        try{
            String fileName = user.getName() + "_" + projectNum + "_" + projectVersion;

            String imgUrl = s3FileService.upload(thumbnail, "project", fileName);

            project.setImgUrl(imgUrl);
            projectsRepository.save(project);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new S3FailedException(e.getMessage());
        }

        return 1;
    }

    @Override
    @Transactional
    public String insertImage(MultipartFile image, Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();

        Long projectNum = project.getNum();
        int projectVersion = project.getVersion();

        try{
            LocalDateTime dateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDateTime = dateTime.format(formatter);
            String fileName = user.getName() + "_" + projectNum + "_" + projectVersion + "_" + formattedDateTime;

            return s3FileService.upload(image, "project/content", fileName);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new S3FailedException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public int deleteImage(String imgUrl, Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();

        String pattern = "(_[0-9]{4}-[0-9]{2}-[0-9]{2}(\\s|%20)[0-9]{2}(%3A|:)[0-9]{2}(%3A|:)[0-9]{2})";

        Pattern regex = Pattern.compile(pattern);
        List<String> fileName = List.of(imgUrl.split("/"));
        Matcher matcher = regex.matcher(fileName.get(fileName.size() - 1));

        if (matcher.find()) {
            String projectString = (fileName.get(fileName.size() - 1)).replace(matcher.group(1), "");
            Long projectNum = project.getNum();
            int projectVersion = project.getVersion();

            if(!projectString.equals(user.getName() + "_" + projectNum + "_" + projectVersion)) throw new NotMatchException();
        }
        try{
            s3FileService.delete(imgUrl.replace("https://d2swdwg2kwda2j.cloudfront.net", "https://roughcode.s3.ap-northeast-2.amazonaws.com"), "project");
            return 1;
        } catch(Exception e){
            log.error(e.getMessage());
            throw new S3FailedException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public int updateProject(ProjectReq req, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 기존의 프로젝트 가져오기
        Projects target = projectsRepository.findByProjectsIdAndExpireDateIsNull(req.getProjectId());
        if(target == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!target.getProjectWriter().equals(user)) throw new NotMatchException();

        Projects latestProject = projectsRepository.findLatestProject(target.getNum(), user.getUsersId());
        if(!target.equals(latestProject)) throw new NotNewestVersionException();

        ProjectsInfo originalInfo = projectsInfoRepository.findByProjects(target);
        if(originalInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        try {
            // tag 삭제
            List<ProjectSelectedTags> selectedTagsList = target.getSelectedTags();
            if(selectedTagsList != null) {
                List<ProjectTags> tagsList = new ArrayList<>();
                for (ProjectSelectedTags tag : selectedTagsList) {
                    ProjectTags projectTag = tag.getTags();
                    projectTag.cntDown();
                    tagsList.add(projectTag);
                }
                projectTagsRepository.saveAll(tagsList);
                projectSelectedTagsRepository.deleteAll(selectedTagsList);
            }else log.info("기존에 선택하였던 tag가 없습니다");

            // tag 등록
            if(req.getSelectedTagsId() != null) {
                List<ProjectSelectedTags> selectedTagssList = new ArrayList<>();
                List<ProjectTags> tagsList = new ArrayList<>();
                for (Long id : req.getSelectedTagsId()) {
                    ProjectTags projectTag = projectTagsRepository.findByTagsId(id);
                    selectedTagssList.add(ProjectSelectedTags.builder()
                            .tags(projectTag)
                            .projects(target)
                            .build());

                    projectTag.cntUp();
                    tagsList.add(projectTag);
                }
                projectSelectedTagsRepository.saveAll(selectedTagssList);
                projectTagsRepository.saveAll(tagsList);
            }else log.info("새로 선택한 tag가 없습니다");

            // feedback 삭제
            List<SelectedFeedbacks> selectedFeedbacksList = target.getSelectedFeedbacks();
            if(selectedFeedbacksList != null) {
                List<Feedbacks> feedbacksList = new ArrayList<>();
                for (SelectedFeedbacks feedback : selectedFeedbacksList) {
                    Feedbacks feedbacks = feedback.getFeedbacks();
                    feedbacks.selectedDown();
                    feedbacksList.add(feedbacks);
                }
                feedbacksRepository.saveAll(feedbacksList);
                selectedFeedbacksRepository.deleteAll(selectedFeedbacksList);
            }else log.info("기존에 선택하였던 feedback이 없습니다");

            // feedback 등록
            if(req.getSelectedFeedbacksId() != null) {
                List<Feedbacks> feedbacksList = new ArrayList<>();
                List<SelectedFeedbacks> selectedFeedbacksListTemp = new ArrayList<>();
                for (Long id : req.getSelectedFeedbacksId()) {
                    Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(id);
                    selectedFeedbacksListTemp.add(SelectedFeedbacks.builder()
                            .projects(target)
                            .feedbacks(feedbacks)
                            .build());

                    feedbacks.selectedUp();
                    feedbacksList.add(feedbacks);
                }
                selectedFeedbacksRepository.saveAll(selectedFeedbacksListTemp);
                feedbacksRepository.saveAll(feedbacksList);
            }else log.info("새로 선택한 feedback이 없습니다");

            target.updateProject(req); // title, introduction 업데이트
            originalInfo.updateProject(req);
        } catch(Exception e){
            log.error(e.getMessage());
            throw new UpdateFailedException(e.getMessage());
        }

        return 1;
    }

    @Override
    @Transactional
    public int connect(Long projectsId, Long usersId, List<Long> codesIdList) {
        Users user = usersRepository.findByUsersId(usersId);
            if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectInfo = projectsInfoRepository.findByProjects(project);
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();
        if(projectInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        // code 연결 해제
        List<Codes> origin = codesRepository.findByProjects(project);
        if(origin != null) {
            for (Codes c : origin) {
                c.setProject(null);
            }
            codesRepository.saveAll(origin);
        } else {
            log.info("기존 연결된 코드가 없습니다");
        }

        // code 연결
        List<Codes> codesList = new ArrayList<>();
        if(codesIdList != null)
            for(Long id : codesIdList) {
                Codes codes = codesRepository.findByCodesIdAndExpireDateIsNull(id);
                if(codes == null) throw new NullPointerException("일치하는 코드가 존재하지 않습니다");
                if(!codes.getCodeWriter().equals(user)) throw new NotMatchException();

                codes.setProject(project);
                codesList.add(codes);
            }
        else {
            log.info("연결하려는 코드가 없습니다");
        }
        codesRepository.saveAll(codesList);

        project.setCodes(codesList);
        projectsRepository.save(project);

        return codesList.size();
    }

    @Override
    @Transactional
    public int putExpireDateProject(Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 기존의 프로젝트 가져오기
        Projects target = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(target == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!target.getProjectWriter().getUsersId().equals(user.getUsersId())) throw new NotMatchException();

        Projects latestProject = projectsRepository.findLatestProject(target.getNum(), user.getUsersId());
        if(!target.equals(latestProject)) throw new NotNewestVersionException();

        target.setExpireDate();
        projectsRepository.save(target);

        Long projectNum = target.getNum();
        int projectVersion = target.getVersion();

        // 관련 사진 전부 삭제
        if(!target.getImg().equals("https://d2swdwg2kwda2j.cloudfront.net/no+image.jpeg"))
            s3FileService.delete(target.getImg().replace("https://d2swdwg2kwda2j.cloudfront.net", "https://roughcode.s3.ap-northeast-2.amazonaws.com"), "project");
        s3FileService.deleteAll("project/content/" + user.getName() + "_" + projectNum + "_" + projectVersion);
        return 1;
    }

    @Override
    @Transactional
    public int deleteProject(Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        // 기존의 프로젝트 가져오기
        Projects target = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(target == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        Projects latestProject = projectsRepository.findLatestProject(target.getNum(), user.getUsersId());
        if(!target.equals(latestProject)) throw new NotNewestVersionException();
        if(!target.getProjectWriter().getUsersId().equals(user.getUsersId())) throw new NotMatchException();

        ProjectsInfo originalInfo = projectsInfoRepository.findByProjects(target);
        if(originalInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        List<ProjectLikes> projectLikes = projectLikesRepository.findByProjects(target);
        if(projectLikes != null) {
            projectLikesRepository.deleteAll(projectLikes);
        }else log.info("기존에 좋아요한 유저가 없습니다");

        Long projectNum = target.getNum();
        int projectVersion = target.getVersion();

        if(target.getProjectsCodes() != null) {
            List<Codes> codesList = new ArrayList<>();
            for (Codes code : target.getProjectsCodes()) {
                code.setProject(null);
                codesList.add(code);
            }
            codesRepository.saveAll(codesList);
        }else log.info("연결된 코드가 없습니다");
        target.setCodes(null);
        projectsRepository.save(target);

        if(target.getSelectedTags() != null) {
            List<ProjectTags> projectTagsList = new ArrayList<>();
            List<ProjectSelectedTags> projectSelectedTags = new ArrayList<>();
            for (ProjectSelectedTags selectedTag : target.getSelectedTags()) {
                ProjectTags projectTag = selectedTag.getTags();
                projectTag.cntDown();
                projectTagsList.add(projectTag);
                projectSelectedTags.add(selectedTag);
            }
            projectTagsRepository.saveAll(projectTagsList);
            projectSelectedTagsRepository.deleteAll(projectSelectedTags);
        }else log.info("연결된 태그가 없습니다");

        // 선택된 feedback 삭제
        List<SelectedFeedbacks> selectedFeedbacksList = target.getSelectedFeedbacks();
        if(selectedFeedbacksList != null) {
            List<Feedbacks> feedbacksList = new ArrayList<>();
            for (SelectedFeedbacks feedback : selectedFeedbacksList) {
                Feedbacks feedbacks = feedback.getFeedbacks();
                feedbacks.selectedDown();
                feedbacksList.add(feedbacks);
            }
            feedbacksRepository.saveAll(feedbacksList);
            selectedFeedbacksRepository.deleteAll(selectedFeedbacksList);
        }else log.info("기존에 선택하였던 feedback이 없습니다");

        // feedback 삭제
        List<Feedbacks> feedbacksList = originalInfo.getFeedbacks();
        if(feedbacksList != null) {
            for (Feedbacks f : feedbacksList) {
                List<FeedbacksLikes> feedbacksLikesList = feedbacksLikesRepository.findByFeedbacks(f);
                feedbacksLikesRepository.deleteAll(feedbacksLikesList);
            }
            feedbacksRepository.deleteAll(feedbacksList);
        }else log.info("기존에 선택하였던 feedback이 없습니다");

        projectsInfoRepository.delete(originalInfo);
        projectsRepository.delete(target);

        // 관련 사진 전부 삭제
        if(!target.getImg().equals("https://d2swdwg2kwda2j.cloudfront.net/no+image.jpeg"))
            s3FileService.delete(target.getImg().replace("https://d2swdwg2kwda2j.cloudfront.net", "https://roughcode.s3.ap-northeast-2.amazonaws.com"), "project");
        s3FileService.deleteAll("project/content/" + user.getName() + "_" + projectNum + "_" + projectVersion);

        return 1;
    }

    @Override
    @Transactional
    public void deleteExpiredProject() {
        LocalDateTime now = LocalDateTime.now();
        List<Projects> expiredProjects = projectsRepository.findByExpireDateBefore(now);

        // 삭제될 프로젝트가 없으면 함수 종료
        if (expiredProjects == null) {
            return;
        }

        for(Projects target: expiredProjects){
            // 연결된 코드에서 프로젝트 제거
            if (target.getProjectsCodes() != null) {
                for (Codes targetCode: target.getProjectsCodes()) {
                    targetCode.setProject(null);
                }
            }
        }

        // 기존에 선택한 태그 삭제
        projectSelectedTagsRepository.deleteAllByProjectsList(expiredProjects);

        // 기존에 선택한 피드백 삭제
        selectedFeedbacksRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트에 등록된 피드백 좋아요 목록 삭제
        feedbacksLikesRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트에 등록딘 피드백 목록 삭제
        feedbacksRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트 좋아요 목록 삭제
        projectLikesRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트 즐겨찾기 목록 삭제
        projectFavoritesRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트 정보 삭제
        projectsInfoRepository.deleteAllByProjectsList(expiredProjects);

        // 프로젝트 삭제
        projectsRepository.deleteAll(expiredProjects);

        // 영속성 컨텍스트 초기화
        entityManager.clear();
    }

    @Override
    @Transactional
    public Pair<List<ProjectInfoRes>, Boolean> getProjectList(Long usersId, String sort, PageRequest pageRequest,
                                                             String keyword, String tagIds, int closed) {
        List<Long> tagIdList = null;
        if(tagIds.length() > 0)
             tagIdList = Arrays.stream(tagIds.split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

        log.info("find project list of" + ((closed == 1)?" closed and opened" : " only opened"));
        if(keyword == null) keyword = "";
        Page<Projects> projectsPage = null;
        if(tagIdList == null || tagIdList.size() == 0){ // tag 검색 x
            if(closed == 1)
                projectsPage = projectsRepository.findAllByKeyword(keyword, pageRequest);
            else
                projectsPage = projectsRepository.findAllOpenedByKeyword(keyword, pageRequest);

        } else { // tag 검색
            if(closed == 1)
                projectsPage = projectSelectedTagsRepository.findAllByKeywordAndTag(keyword, tagIdList, (long) tagIdList.size(), pageRequest);
            else
                projectsPage = projectSelectedTagsRepository.findAllOpenedByKeywordAndTag(keyword, tagIdList, (long) tagIdList.size(), pageRequest);

        }

        Pair<List<ProjectInfoRes>, Boolean> res = Pair.of(getProjectInfoRes(projectsPage, usersRepository.findByUsersId(usersId)), projectsPage.hasNext());
        return res;
    }

    @Override
    @Transactional
    public ProjectDetailRes getProject(Long projectId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        List<ProjectTagsRes> tagList = getTags(project);
        ProjectFavorites myFavorite = (user != null)? projectFavoritesRepository.findByProjectsAndUsers(project, user) : null;
        ProjectLikes myLike = (user != null) ? projectLikesRepository.findByProjectsAndUsers(project, user) : null;
        Boolean liked = myLike != null;
        Boolean favorite = myFavorite != null;

        ProjectDetailRes projectDetailRes = new ProjectDetailRes(project, projectsInfo, tagList, liked, favorite, user);

        List<Pair<Projects, ProjectsInfo>> otherVersions = new ArrayList<>();
        List<Projects> projectList = projectsRepository.findByNumAndProjectWriterAndExpireDateIsNullOrderByVersionDesc(project.getNum(), project.getProjectWriter());
        for (Projects p : projectList) {
            otherVersions.add(Pair.of(p, projectsInfoRepository.findByProjects(p)));
        }

        List<VersionRes> versionResList = new ArrayList<>();
        for (Pair<Projects, ProjectsInfo> p : otherVersions) {
            List<SelectedFeedbacksRes> feedbacksResList = new ArrayList<>();
            if(p.getLeft().getSelectedFeedbacks() != null)
                for (var feedbacks : p.getLeft().getSelectedFeedbacks()) {
                    feedbacksResList.add(SelectedFeedbacksRes.builder()
                            .feedbackId(feedbacks.getFeedbacks().getFeedbacksId())
                            .content(feedbacks.getFeedbacks().getContent())
                            .build());
                }
            versionResList.add(VersionRes.builder()
                    .selectedFeedbacks(feedbacksResList)
                    .notice(p.getRight().getNotice())
                    .projectId(p.getLeft().getProjectsId())
                    .version(p.getLeft().getVersion())
                    .date(p.getLeft().getCreatedDate())
                    .build());
        }
        projectDetailRes.setVersions(versionResList);

        List<FeedbackRes> feedbackResList = new ArrayList<>();
        if(projectsInfo.getFeedbacks() != null)
            for (Feedbacks f : projectsInfo.getFeedbacks()) {
                FeedbacksLikes feedbackLike = (user != null)? feedbacksLikesRepository.findByFeedbacksAndUsers(f, user) : null;
                Boolean feedbackLiked = feedbackLike != null;
                feedbackResList.add(new FeedbackRes(f, feedbackLiked));
            }
        // Selected가 우선, usersId가 같은것이 더 앞, 이후 최신순
        feedbackResList.sort(Comparator.comparing(FeedbackRes::getSelected).reversed()
                .thenComparing((f1, f2) -> {
                    if ((f1.getUserId() != null && f1.getUserId().equals(usersId)) && (f2.getUserId() == null || !f2.getUserId().equals(usersId))) {
                        return -1;
                    } else if ((f1.getUserId() == null || !f1.getUserId().equals(usersId)) && (f2.getUserId() != null && f2.getUserId().equals(usersId))) {
                        return 1;
                    } else {
                        return f2.getDate().compareTo(f1.getDate());
                    }
                }));
        projectDetailRes.setFeedbacks(feedbackResList);

        return projectDetailRes;
    }

    @Override
    @Transactional
    public int likeProject(Long projectsId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        // 이미 좋아요 한 프로젝트인지 확인
        ProjectLikes projectLikes = projectLikesRepository.findByProjectsAndUsers(project, users);
        if(projectLikes != null) { // 좋아요 취소
            projectLikesRepository.delete(projectLikes);

            project.likeCntDown();
            projectsRepository.save(project);
            return 0;
        }
        else{ // 프로젝트 좋아요
            projectLikesRepository.save(ProjectLikes.builder()
                    .projects(project)
                    .users(users)
                    .build());

            project.likeCntUp();
            projectsRepository.save(project);
            return 1;
        }
    }

    @Override
    @Transactional
    public int favoriteProject(Long projectsId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        // 이미 즐겨찾기 한 프로젝트인지 확인
        ProjectFavorites projectFavorites = projectFavoritesRepository.findByProjectsAndUsers(project, users);
        if(projectFavorites != null) { // 즐겨찾기 취소
            projectFavoritesRepository.delete(projectFavorites);

            projectsInfo.favoriteCntDown();
            projectsInfoRepository.save(projectsInfo);
            return 0;
        }
        else{ // 프로젝트 즐겨찾기
            projectFavoritesRepository.save(ProjectFavorites.builder()
                    .projects(project)
                    .users(users)
                    .build());

            projectsInfo.favoriteCntUp();
            projectsInfoRepository.save(projectsInfo);
            return 1;
        }
    }

    @Override
    @Transactional
    public int openProject(Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();

        Projects latestProject = projectsRepository.findLatestProject(project.getNum(), usersId);
        if(!project.equals(latestProject)) throw new NotNewestVersionException();

        project.setStatus(false);

        return 1;
    }

    @Override
    @Transactional
    public int closeProject(Long projectsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);
        if(user == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectsId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        if(!project.getProjectWriter().equals(user)) throw new NotMatchException();

        Projects latestProject = projectsRepository.findLatestProject(project.getNum(), usersId);
        if(!project.equals(latestProject)) throw new NotNewestVersionException();

        project.setStatus(true);

        return 1;
    }

    @Override
    @Transactional
    public int isProjectOpen(Long projectId) throws MessagingException, IOException {
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectId);
            if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");


        if(isOpen(projectsInfo.getUrl())) {
            if(!checkProject(projectsInfo.getUrl(), true)) return 0;
            projectsInfo.setClosedChecked(null);
            return 1;
        }
        LocalDateTime now = LocalDateTime.now();
        if (projectsInfo.getClosedChecked() == null) { // 처음 닫힌 것이 확인됨
            AlarmReq alarmContent = AlarmReq.builder()
                    .content(List.of("", project.getTitle() + " ver" + project.getVersion(), "이 닫혔는지 확인 요청 드립니다"))
                    .userId(project.getProjectWriter().getUsersId())
                    .postId(project.getProjectsId())
                    .section("project")
                    .build();
            // 무슨무슨 프로젝트 ver1 닫힘 확인 요청 -> [“”, “무슨무슨 프로젝트 ver1”, “닫힘 확인 요청”]
            alarmService.insertAlarm(alarmContent);
            projectsInfo.setClosedChecked(now);
            projectsInfoRepository.save(projectsInfo);

            emailService.sendAlarm("프로젝트가 닫혀있나요?", alarmContent);
        } else { // 전에도 닫혀있음이 확인됐었음
            if (now.isAfter(projectsInfo.getClosedChecked().plusMinutes(60))) { // 1시간 이상 지났으면
                AlarmReq alarmContent = AlarmReq.builder()
                        .content(List.of("프로젝트 ", project.getTitle() + " ver" + project.getVersion(), "확인 요청 후 1시간이 초과되어 닫힘 상태로 변경"))
                        .userId(project.getProjectWriter().getUsersId())
                        .postId(project.getProjectsId())
                        .section("project")
                        .build();
                // 무슨무슨 프로젝트 ver1 확인 요청 후 1시간이 초과되어 닫힘 상태로 변경 -> [“”, “무슨무슨 프로젝트 ver1”, “확인 요청 후 1시간이 초과되어 닫힘 상태로 변경”]
                alarmService.insertAlarm(alarmContent);
                project.close(true);
                projectsRepository.save(project);

                emailService.sendAlarm("프로젝트를 닫았습니다", alarmContent);
                return -1;
            }
        }

        return 0;
    }

    public boolean isOpen(String url) {
        try {
            URL u = new URL(url);
            HttpURLConnection huc = (HttpURLConnection) u.openConnection();
            huc.setRequestMethod("GET");
            huc.connect();
            int responseCode = huc.getResponseCode();
            log.info(url + " responseCode : " + responseCode);
            // 400 이상은 안전하지 않은 URL로 판단
            return responseCode < 300;
        } catch (Exception e) {
            return false;
        }
    }

    @Value("${filepath.google-credentials}")
    String credentialsPath = "google-credentials.json"; // 인증 정보 파일 경로

    @Override
    @Transactional
    public Boolean checkProject(String url, boolean open) throws IOException {
        if(!open)
            if (!isOpen(url))
                throw new IOException("서버 확인이 필요한 URL입니다");

        SearchUrisResponse searchUrisResponse;
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream stream = classLoader.getResourceAsStream(credentialsPath);
            if(stream == null) log.error("credential 정보가 없습니다");
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);

            WebRiskServiceSettings settings =
                    WebRiskServiceSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();

            // create-webrisk-client
            try (WebRiskServiceClient webRiskServiceClient = WebRiskServiceClient.create(settings)) {
                // Query the url for a specific threat type
                SearchUrisRequest searchUrisRequest =
                        SearchUrisRequest.newBuilder().addThreatTypes(ThreatType.MALWARE).setUri(url).build();
                searchUrisResponse = webRiskServiceClient.searchUris(searchUrisRequest);
                webRiskServiceClient.shutdownNow();
                if (!searchUrisResponse.getThreat().getThreatTypesList().isEmpty()) {
                    log.info("The URL has the following threat : ");
                    log.info(String.valueOf(searchUrisResponse));
                } else {
                    log.info("The URL is safe!");
                }
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        return searchUrisResponse.getThreat().getThreatTypesList().isEmpty();
    }

    @Override
    @Transactional
    public int insertFeedback(FeedbackInsertReq req, Long usersId) throws MessagingException {
        Users users = usersRepository.findByUsersId(usersId);
        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(req.getProjectId());
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");
        ProjectsInfo projectsInfo = projectsInfoRepository.findByProjects(project);
        if(projectsInfo == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        Feedbacks savedFeedback = feedbacksRepository.save(
                Feedbacks.builder()
                        .projectsInfo(projectsInfo)
                        .content(req.getContent())
                        .users(users)
                        .build()
        );
        projectsInfo.setFeedbacks(savedFeedback);
        projectsInfoRepository.save(projectsInfo);

        project.feedbackCntUp();
        projectsRepository.save(project);

        AlarmReq alarmContent = AlarmReq.builder()
                .content(List.of("작성한 프로젝트, ", project.getTitle() + " ver" + project.getVersion(), "에 새 피드백이 등록되었습니다"))
                .userId(project.getProjectWriter().getUsersId())
                .postId(project.getProjectsId())
                .section("project")
                .build();

        // 작성한 무슨무슨 프로젝트 ver1에 새 피드백 등록 -> [“작성한”, “무슨무슨 프로젝트 ver1”, “새 피드백 등록”]
        alarmService.insertAlarm(alarmContent);

        emailService.sendAlarm("새 피드백이 등록되었습니다", alarmContent);

        return projectsInfo.getFeedbacks().size();
    }

    @Override
    @Transactional
    public Boolean updateFeedback(FeedbackUpdateReq req, Long userId) {
        Users users = usersRepository.findByUsersId(userId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(req.getFeedbackId());
        if(feedbacks == null) throw new NullPointerException("일치하는 피드백이 존재하지 않습니다");
        else if(feedbacks.getUsers() == null || !feedbacks.getUsers().equals(users)) throw new NotMatchException();
        else if(feedbacks.getSelected() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "채택된 피드백은 수정할 수 없습니다");

        feedbacks.editContent(req.getContent());
        Feedbacks updated = feedbacksRepository.save(feedbacks);

        return updated.getContent().equals(req.getContent());
    }

    @Override
    @Transactional
    public List<FeedbackInfoRes> getFeedbackList(Long projectId, Long usersId, boolean versionUp) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Projects project = projectsRepository.findByProjectsIdAndExpireDateIsNull(projectId);
        if(project == null) throw new NullPointerException("일치하는 프로젝트가 존재하지 않습니다");

        List<Projects> allVersion = projectsRepository.findByNumAndProjectWriterAndExpireDateIsNullOrderByVersionDesc(project.getNum(), users);

        int idx = (versionUp)? 0 : 1;
        List<FeedbackInfoRes> feedbackInfoResList = new ArrayList<>();
        for (; idx < allVersion.size(); idx++) {
            ProjectsInfo info = projectsInfoRepository.findByProjects(allVersion.get(idx));
            List<Feedbacks> feedbacksList = info.getFeedbacks();
            for (Feedbacks f : feedbacksList) {
                if(f.getComplained() != null) continue; // 신고된 피드백
                feedbackInfoResList.add(new FeedbackInfoRes(f, allVersion.get(idx).getVersion(), f.getUsers()));
            }
        }

        return feedbackInfoResList;
    }

    @Override
    @Transactional
    public int deleteFeedback(Long feedbackId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(feedbackId);
        if(feedbacks == null) throw new NullPointerException("일치하는 피드백이 존재하지 않습니다");

        if(feedbacks.getUsers() == null || !feedbacks.getUsers().equals(users)) throw new NotMatchException();

        if(feedbacks.getSelected() > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "채택된 피드백은 삭제할 수 없습니다");

        Projects projects = feedbacks.getProjectsInfo().getProjects();
        projects.feedbackCntDown();
        projectsRepository.save(projects);

        List<FeedbacksLikes> feedbacksLikes = feedbacks.getFeedbacksLikes();
        if(feedbacksLikes != null)
            feedbacksLikesRepository.deleteAll(feedbacksLikes);

        feedbacksRepository.delete(feedbacks);
        return 1;
    }

    @Override
    @Transactional
    public int feedbackComplain(Long feedbackId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(feedbackId);
        if(feedbacks == null)
            throw new NullPointerException("일치하는 피드백이 존재하지 않습니다");

        if(feedbacks.getUsers() != null && feedbacks.getUsers().getUsersId().equals(users.getUsersId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "피드백 작성자와 신고 유저가 동일합니다");

        if(feedbacks.getComplained() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 삭제된 피드백입니다");

        FeedbacksComplains complains = feedbacksComplainsRepository.findByFeedbacksAndUsers(feedbacks, users);
        if(complains != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 피드백입니다");

        List<FeedbacksComplains> complainList = feedbacksComplainsRepository.findByFeedbacks(feedbacks);

        log.info(complainList.size() + "번 신고된 피드백입니다");

        FeedbacksComplains newComplain = FeedbacksComplains.builder()
                .feedbacks(feedbacks)
                .users(users)
                .build();

        feedbacksComplainsRepository.save(newComplain);

        if(complainList.size() + 1 >= 5){
            feedbacks.setComplained();
            feedbacksRepository.save(feedbacks);
        }

        return (feedbacks.getComplained() == null)? 0 : 1;
    }

    @Override
    @Transactional
    public int likeProjectFeedback(Long feedbackId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        Feedbacks feedbacks = feedbacksRepository.findByFeedbacksId(feedbackId);
        if(feedbacks == null)
            throw new NullPointerException("일치하는 피드백이 존재하지 않습니다");

        // 이미 좋아요 한 피드백인지 확인
        FeedbacksLikes feedbacksLikes = feedbacksLikesRepository.findByFeedbacksAndUsers(feedbacks, users);
        if(feedbacksLikes != null) { // 피드백 좋아요 취소
            feedbacksLikesRepository.delete(feedbacksLikes);

            feedbacks.likeCntDown();
            feedbacksRepository.save(feedbacks);
            return 0;
        }
        else{ // 프로젝트 좋아요
            feedbacksLikesRepository.save(FeedbacksLikes.builder()
                    .feedbacks(feedbacks)
                    .users(users)
                    .build());

            feedbacks.likeCntUp();
            feedbacksRepository.save(feedbacks);
            return 1;
        }
    }

    @Override
    @Transactional
    public List<ProjectTagsRes> searchTags(String keyword) {
        List<ProjectTags> tags = projectTagsRepository.findAllByNameContaining(keyword, Sort.by(Sort.Direction.ASC, "name"));
        List<ProjectTagsRes> result = new ArrayList<>();
        for (ProjectTags tag : tags) {
            result.add(new ProjectTagsRes(tag));
        }
        return result;
    }

    private List<ProjectInfoRes> getProjectInfoRes(Page<Projects> projectsPage, Users user) {
        List<Projects> projectList = projectsPage.getContent();
        List<ProjectInfoRes> projectInfoRes = new ArrayList<>();
        for (Projects p : projectList) {
            List<ProjectTagsRes> tagList = getTags(p);
            ProjectLikes projectLikes = null;
            if(user != null) projectLikes = projectLikesRepository.findByProjectsAndUsers(p, user);

            projectInfoRes.add(ProjectInfoRes.builder()
                    .date(p.getCreatedDate())
                    .img(p.getImg())
                    .projectId(p.getProjectsId())
                    .feedbackCnt(p.getFeedbackCnt())
                    .introduction(p.getIntroduction())
                    .likeCnt(p.getLikeCnt())
                    .liked(projectLikes != null)
                    .tags(tagList)
                    .title(p.getTitle())
                    .version(p.getVersion())
                    .closed(p.isClosed())
                    .build()
            );
        }
        return projectInfoRes;
    }

    private static List<ProjectTagsRes> getTags(Projects p) {
        List<ProjectTagsRes> tagList = new ArrayList<>();
        if(p.getSelectedTags() != null)
            for (ProjectSelectedTags selected : p.getSelectedTags()) {
                tagList.add(
                        ProjectTagsRes.builder()
                                .tagId(selected.getTags().getTagsId())
                                .name(selected.getTags().getName())
                                .cnt(selected.getTags().getCnt())
                                .build());
            }
        return tagList;
    }

}


