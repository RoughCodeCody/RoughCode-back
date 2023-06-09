package com.cody.roughcode.mypage.controller;

import com.cody.roughcode.alarm.entity.Alarm;
import com.cody.roughcode.alarm.service.AlarmServiceImpl;
import com.cody.roughcode.code.dto.res.CodeInfoRes;
import com.cody.roughcode.code.dto.res.CodeTagsRes;
import com.cody.roughcode.code.entity.Codes;
import com.cody.roughcode.email.service.EmailServiceImpl;
import com.cody.roughcode.mypage.service.MypageServiceImpl;
import com.cody.roughcode.project.dto.res.ProjectInfoRes;
import com.cody.roughcode.project.dto.res.ProjectTagsRes;
import com.cody.roughcode.project.entity.Projects;
import com.cody.roughcode.security.auth.JwtProperties;
import com.cody.roughcode.security.auth.JwtTokenProvider;
import com.cody.roughcode.user.entity.Users;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.cody.roughcode.user.enums.Role.ROLE_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class MypageControllerTest {
    static{
        System.setProperty("com.amazonaws.sdk.disableEc2Metadata", "true");
    }

    @InjectMocks
    private MypageController target;

    private MockMvc mockMvc;
    private Gson gson;

    @BeforeEach
    public void init(){
        gson = new Gson();
        mockMvc = MockMvcBuilders.standaloneSetup(target)
                .build();
    }

    final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6MSwibmFtZSI6Imtvc3kzMTgiLCJhdXRoIjoiUk9MRV9VU0VSIiwiZXhwIjoxNjgzNTkzNzU0fQ.InDlkf3NHCoDcOGCnEaB8Wc7qtEPz0hCWulUkgQEtCY";

    final Users users = Users.builder()
            .usersId(1L)
            .email("kosy1782@gmail.com")
            .name("kosy318")
            .roles(List.of(String.valueOf(ROLE_USER)))
            .build();

    final Alarm alarm1 = Alarm.builder()
            .createdDate(LocalDateTime.now())
            .postId(1L)
            .section("project")
            .content(List.of("test", "project1", "test"))
            .userId(1L)
            .build();
    final Alarm alarm2 = Alarm.builder()
            .createdDate(LocalDateTime.now())
            .postId(2L)
            .section("project")
            .content(List.of("test", "project2", "test"))
            .userId(1L)
            .build();

    final Alarm alarm3 = Alarm.builder()
            .createdDate(LocalDateTime.now())
            .postId(1L)
            .section("project")
            .content(List.of("test", "project1", "test"))
            .userId(2L)
            .build();

    @Mock
    private AlarmServiceImpl alarmService;
    @Mock
    private MypageServiceImpl mypageService;
    @Mock
    private EmailServiceImpl emailService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    final Codes code = Codes.builder()
            .codesId(1L)
            .num(1L)
            .version(1)
            .codeWriter(users)
            .title("title")
            .reviewCnt(1)
            .build();

    private List<ProjectTagsRes> projectTagsResInit() {
        List<String> list = List.of("SpringBoot", "React", "AWS");
        List<ProjectTagsRes> tagsList = new ArrayList<>();
        for (long i = 1L; i <= 3L; i++) {
            tagsList.add(ProjectTagsRes.builder()
                    .tagId(i)
                    .name(list.get((int)i-1))
                    .cnt(0)
                    .build());
        }

        return tagsList;
    }

    @DisplayName("스탯 카드 정보 가져오기 성공 - 기존 포맷 - cookie")
    @Test
    public void makeStatCardFormatWithCookieSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/stat";
        doReturn(users.getUsersId()).when(jwtTokenProvider).getId(any(String.class));
        doReturn("string").when(mypageService).makeStatCardWithUserId(eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        String result = jsonObject.get("result").getAsString();
        assertThat(message).isEqualTo("스탯 카드 정보 만들기 성공");
        assertThat(result).isEqualTo("string");
    }

    @DisplayName("스탯 카드 정보 가져오기 성공 - 기존 포맷 - userName")
    @Test
    public void makeStatCardFormatWithUserNameSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/stat";
        doReturn("string").when(mypageService).makeStatCardWithUserName(eq(users.getName()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .param("userName", users.getName())
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        String result = jsonObject.get("result").getAsString();
        assertThat(message).isEqualTo("스탯 카드 정보 만들기 성공");
        assertThat(result).isEqualTo("string");
    }

    @DisplayName("스탯 카드 정보 가져오기 성공 - cookie")
    @Test
    public void makeStatCardWithCookieSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage";
        doReturn(users.getUsersId()).when(jwtTokenProvider).getId(any(String.class));
        doReturn("string").when(mypageService).makeStatCardWithUserId(eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseBody).isEqualTo("string");
    }

    @DisplayName("스탯 카드 정보 가져오기 성공 - userName")
    @Test
    public void makeStatCardWithUserNameSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage";
        doReturn("string").when(mypageService).makeStatCardWithUserName(eq(users.getName()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .param("userName", users.getName())
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(responseBody).isEqualTo("string");
    }

    @DisplayName("이메일 정보 삭제 실패 - 존재하지 않는 유저")
    @Test
    public void deleteEmailFailNoUser() throws Exception {
        // given
        final String url = "/api/v1/mypage/email";
        doReturn(1L).when(jwtTokenProvider).getId(eq(accessToken));
        doThrow(new NullPointerException("일치하는 유저가 존재하지 않습니다")).when(emailService).deleteEmailInfo(eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.delete(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isBadRequest()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("일치하는 유저가 존재하지 않습니다");
    }


    @DisplayName("이메일 정보 삭제 실패")
    @Test
    public void deleteEmailFail() throws Exception {
        // given
        final String url = "/api/v1/mypage/email";
        doReturn(1L).when(jwtTokenProvider).getId(eq(accessToken));
        doReturn("not null").when(emailService).deleteEmailInfo(eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.delete(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isNotFound()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("이메일 정보 삭제 실패");
    }

    @DisplayName("이메일 정보 삭제 성공")
    @Test
    public void deleteEmailSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/email";
        doReturn(1L).when(jwtTokenProvider).getId(eq(accessToken));
        doReturn("").when(emailService).deleteEmailInfo(eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.delete(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("이메일 정보 삭제 성공");
    }

    @DisplayName("이메일 인증 성공")
    @Test
    public void checkEmailSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/email";
        String code = "12345678";
        doReturn(1L).when(jwtTokenProvider).getId(eq(accessToken));
        doReturn(true).when(emailService).checkEmail(eq(users.getEmail()), eq(code), eq(users.getUsersId()));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.put(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("email", users.getEmail())
                        .param("code", code)
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        String result = jsonObject.get("result").getAsString();
        assertThat(message).isEqualTo("이메일 인증 성공");
        assertThat(result).isEqualTo("1");
    }

    @DisplayName("이메일 인증 코드 보내기 성공")
    @Test
    public void sendCertificationEmailSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/email";
        doReturn(1L).when(jwtTokenProvider).getId(eq(accessToken));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.post(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("email", users.getEmail())
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("이메일 전송 성공");
    }

    @DisplayName("즐겨찾기한 코드 목록 조회 성공")
    @Test
    public void getFavoriteCodeListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/code/favorite";

        int page = 10;

        List<CodeInfoRes> codeInfoRes = List.of(
                CodeInfoRes.builder()
                        .codeId(code.getCodesId())
                        .version(code.getVersion())
                        .title(code.getTitle())
                        .date(code.getCreatedDate())
                        .likeCnt(code.getLikeCnt())
                        .reviewCnt(code.getReviewCnt())
                        .userName(code.getCodeWriter().getName())
                        .build()
        );

        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(codeInfoRes, false)).when(mypageService)
                .getFavoriteCodeList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("내 즐겨찾기 코드 목록 조회 성공");
    }

    @DisplayName("리뷰한 코드 목록 조회 성공")
    @Test
    public void getReviewCodeListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/code/review";

        int page = 10;

        List<CodeInfoRes> codeInfoRes = List.of(
                CodeInfoRes.builder()
                        .codeId(code.getCodesId())
                        .version(code.getVersion())
                        .title(code.getTitle())
                        .date(code.getCreatedDate())
                        .likeCnt(code.getLikeCnt())
                        .reviewCnt(code.getReviewCnt())
                        .userName(code.getCodeWriter().getName())
                        .build()
        );

        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(codeInfoRes, false)).when(mypageService)
                .getReviewCodeList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("리뷰한 코드 목록 조회 성공");
    }

    @DisplayName("코드 목록 조회 성공")
    @Test
    public void getCodeListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/code";

        int page = 10;

        List<CodeInfoRes> codeInfoRes = List.of(
                CodeInfoRes.builder()
                        .codeId(code.getCodesId())
                        .version(code.getVersion())
                        .title(code.getTitle())
                        .date(code.getCreatedDate())
                        .likeCnt(code.getLikeCnt())
                        .reviewCnt(code.getReviewCnt())
                        .userName(code.getCodeWriter().getName())
                        .build()
        );
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(codeInfoRes, false)).when(mypageService)
                .getCodeList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("내 코드 목록 조회 성공");
    }

    @DisplayName("피드백한 프로젝트 목록 조회 성공")
    @Test
    public void getFeedbackProjectListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/project/feedback";

        int page = 10;

        final Projects project = Projects.builder()
                .projectsId(1L)
                .num(1L)
                .version(1)
                .img("https://roughcode.s3.ap-northeast-2.amazonaws.com/project/7_1")
                .introduction("intro")
                .title("title")
                .projectWriter(users)
                .build();
        List<ProjectInfoRes> projectInfoRes = List.of(
                ProjectInfoRes.builder()
                        .date(project.getCreatedDate())
                        .img(project.getImg())
                        .projectId(project.getProjectsId())
                        .feedbackCnt(project.getFeedbackCnt())
                        .introduction(project.getIntroduction())
                        .likeCnt(project.getLikeCnt())
                        .tags(projectTagsResInit())
                        .title(project.getTitle())
                        .version(project.getVersion())
                        .build()
        );
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(projectInfoRes, false)).when(mypageService)
                .getFeedbackProjectList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("피드백한 프로젝트 목록 조회 성공");
    }

    @DisplayName("내 즐겨찾기 프로젝트 목록 조회 성공")
    @Test
    public void getFavoriteProjectListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/project/favorite";

        int page = 10;

        final Projects project = Projects.builder()
                .projectsId(1L)
                .num(1L)
                .version(1)
                .img("https://roughcode.s3.ap-northeast-2.amazonaws.com/project/7_1")
                .introduction("intro")
                .title("title")
                .projectWriter(users)
                .build();
        List<ProjectInfoRes> projectInfoRes = List.of(
                ProjectInfoRes.builder()
                        .date(project.getCreatedDate())
                        .img(project.getImg())
                        .projectId(project.getProjectsId())
                        .feedbackCnt(project.getFeedbackCnt())
                        .introduction(project.getIntroduction())
                        .likeCnt(project.getLikeCnt())
                        .tags(projectTagsResInit())
                        .title(project.getTitle())
                        .version(project.getVersion())
                        .build()
        );
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(projectInfoRes, false)).when(mypageService)
                .getFavoriteProjectList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("내 즐겨찾기 프로젝트 목록 조회 성공");
    }

    @DisplayName("프로젝트 목록 조회 성공")
    @Test
    public void getProjectListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/project";

        int page = 10;

        final Projects project = Projects.builder()
                .projectsId(1L)
                .num(1L)
                .version(1)
                .img("https://roughcode.s3.ap-northeast-2.amazonaws.com/project/7_1")
                .introduction("intro")
                .title("title")
                .projectWriter(users)
                .build();
        List<ProjectInfoRes> projectInfoRes = List.of(
                ProjectInfoRes.builder()
                        .date(project.getCreatedDate())
                        .img(project.getImg())
                        .projectId(project.getProjectsId())
                        .feedbackCnt(project.getFeedbackCnt())
                        .introduction(project.getIntroduction())
                        .likeCnt(project.getLikeCnt())
                        .tags(projectTagsResInit())
                        .title(project.getTitle())
                        .version(project.getVersion())
                        .build()
        );
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(Pair.of(projectInfoRes, false)).when(mypageService)
                .getProjectList(any(PageRequest.class), any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
                        .param("page", String.valueOf(page))
                        .param("size", "10")
        );

        // then
        // HTTP Status가 OK인지 확인
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("내 프로젝트 목록 조회 성공");
    }

    @DisplayName("알람 삭제 성공")
    @Test
    public void deleteAlarmSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/alarm/{alarmId}";
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.delete(url, "60a957bcf77ec21e1aa72c93")
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("알림 삭제 성공");
    }

    @DisplayName("알람 목록 조회 성공")
    @Test
    public void getAlarmListSucceed() throws Exception {
        // given
        final String url = "/api/v1/mypage/alarm";
        doReturn(1L).when(jwtTokenProvider).getId(any(String.class));
        doReturn(List.of(alarm1, alarm2)).when(alarmService).getAlarmList(any(Long.class));

        // when
        final ResultActions resultActions = mockMvc.perform(
                MockMvcRequestBuilders.get(url)
                        .cookie(new Cookie(JwtProperties.ACCESS_TOKEN, accessToken))
        );

        // then
        MvcResult mvcResult = resultActions.andExpect(status().isOk()).andReturn();
        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        assertThat(message).isEqualTo("알림 조회 성공");
    }
}
