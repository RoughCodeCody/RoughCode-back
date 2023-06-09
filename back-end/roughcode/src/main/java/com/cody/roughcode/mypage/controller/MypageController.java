package com.cody.roughcode.mypage.controller;

import com.cody.roughcode.alarm.dto.res.AlarmRes;
import com.cody.roughcode.alarm.service.AlarmServiceImpl;
import com.cody.roughcode.code.dto.res.CodeInfoRes;
import com.cody.roughcode.email.service.EmailServiceImpl;
import com.cody.roughcode.mypage.service.MypageServiceImpl;
import com.cody.roughcode.project.dto.res.ProjectInfoRes;
import com.cody.roughcode.security.auth.JwtProperties;
import com.cody.roughcode.security.auth.JwtTokenProvider;
import com.cody.roughcode.util.Response;
import com.cody.roughcode.validation.ObjectId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@Slf4j
@RestController
@RequestMapping("/api/v1/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AlarmServiceImpl alarmService;
    private final MypageServiceImpl mypageService;
    private final EmailServiceImpl emailService;

    @Operation(summary = "스탯 카드 가져오기 API - 기존 형태")
    @GetMapping("/stat")
    public ResponseEntity<?> makeStatCardFormat(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = false) String accessToken,
                                          @Parameter(description = "유저 이름")
                                          @RequestParam(required = false) String userName){
        if(accessToken == null && (userName == null || userName.equals(""))) return Response.badRequest("유저의 정보를 입력해주세요");

        String statCard = "";
        try {
            if (accessToken != null) {
                Long userId = jwtTokenProvider.getId(accessToken);
                if(userId <= 0) return Response.badRequestNoUser();

                statCard = mypageService.makeStatCardWithUserId(userId);
            } else {
                statCard = mypageService.makeStatCardWithUserName(userName);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        return Response.makeResponse(HttpStatus.OK, "스탯 카드 정보 만들기 성공", 1, statCard);
    }

    @Operation(summary = "스탯 카드 가져오기 API")
    @GetMapping(produces = "image/svg+xml")
    public ResponseEntity<?> makeStatCard(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = false) String accessToken,
                                          @Parameter(description = "유저 이름")
                                          @RequestParam(required = false) String userName){
        if(accessToken == null && (userName == null || userName.equals(""))) return Response.badRequest("유저의 정보를 입력해주세요");

        String statCard = "";
        try {
            if (accessToken != null) {
                Long userId = jwtTokenProvider.getId(accessToken);
                if(userId <= 0) return Response.badRequestNoUser();

                statCard = mypageService.makeStatCardWithUserId(userId);
            } else {
                statCard = mypageService.makeStatCardWithUserName(userName);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        return new ResponseEntity<>(statCard, HttpStatus.OK);
        //return Response.makeResponse(HttpStatus.OK, "스탯 카드 정보 만들기 성공", 1, statCard);
    }

    @Operation(summary = "이메일 정보 삭제 API")
    @DeleteMapping("/email")
    public ResponseEntity<?> deleteEmailInfo(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        String res = "result";
        try {
            res = emailService.deleteEmailInfo(usersId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        if(!res.equals("")) return Response.notFound("이메일 정보 삭제 실패");
        return Response.ok("이메일 정보 삭제 성공");
    }

    @Operation(summary = "이메일 인증 API")
    @PutMapping("/email")
    public ResponseEntity<?> checkEmail(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                        @Parameter(description = "이메일", required = true)
                                         @Email(message = "이메일 형식이 아닙니다")
                                         @RequestParam String email,
                                        @Parameter(description = "코드", required = true)
                                        @Pattern(regexp = "^[a-zA-Z0-9]{8}$")
                                        @RequestParam String code) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        try {
            boolean checked = emailService.checkEmail(email, code, usersId);
            return Response.makeResponse(HttpStatus.OK, "이메일 인증 성공", 1, (checked)? "1" : "0");
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }
    }

    @Operation(summary = "이메일 인증 코드 보내기 API")
    @PostMapping("/email")
    public ResponseEntity<?> sendCertificationEmail(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                                    @Parameter(description = "이메일", required = true)
                                                     @Email(message = "이메일 형식이 아닙니다")
                                                     @RequestParam String email) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        try {
            emailService.sendCertificationEmail(email, usersId);
            return Response.ok("이메일 전송 성공");
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }
    }

    @Operation(summary = "즐겨찾기한 코드 목록 조회 API")
    @GetMapping("/code/favorite")
    ResponseEntity<?> getFavoriteCodeList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                          @Parameter(description = "페이지 수")
                                          @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                          @RequestParam(defaultValue = "0") int page,
                                          @Parameter(description = "한 페이지에 담기는 개수")
                                          @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                          @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        Pair<List<CodeInfoRes>, Boolean> res;
        List<CodeInfoRes> codeRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size);
            res = mypageService.getFavoriteCodeList(pageRequest, usersId);
            codeRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", codeRes);
        return Response.makeResponse(HttpStatus.OK, "내 즐겨찾기 코드 목록 조회 성공", codeRes.size(), resultMap);
    }

    @Operation(summary = "리뷰한 코드 목록 조회 API")
    @GetMapping("/code/review")
    ResponseEntity<?> getReviewCodeList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                        @Parameter(description = "페이지 수")
                                        @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                        @RequestParam(defaultValue = "0") int page,
                                        @Parameter(description = "한 페이지에 담기는 개수")
                                        @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                        @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        Pair<List<CodeInfoRes>, Boolean> res;
        List<CodeInfoRes> codeRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size);
            res = mypageService.getReviewCodeList(pageRequest, usersId);
            codeRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", codeRes);
        return Response.makeResponse(HttpStatus.OK, "리뷰한 코드 목록 조회 성공", codeRes.size(), resultMap);
    }

    @Operation(summary = "코드 목록 조회 API")
    @GetMapping("/code")
    ResponseEntity<?> getCodeList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                  @Parameter(description = "페이지 수")
                                  @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                  @RequestParam(defaultValue = "0") int page,
                                  @Parameter(description = "한 페이지에 담기는 개수")
                                  @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                  @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        Pair<List<CodeInfoRes>, Boolean> res;
        List<CodeInfoRes> codeRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
            res = mypageService.getCodeList(pageRequest, usersId);
            codeRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", codeRes);
        return Response.makeResponse(HttpStatus.OK, "내 코드 목록 조회 성공", codeRes.size(), resultMap);
    }

    @Operation(summary = "피드백한 프로젝트 목록 조회 API")
    @GetMapping("/project/feedback")
    ResponseEntity<?> getFeedbackProjectList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                             @Parameter(description = "페이지 수")
                                             @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                             @RequestParam(defaultValue = "0") int page,
                                             @Parameter(description = "한 페이지에 담기는 개수")
                                             @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                             @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        Pair<List<ProjectInfoRes>, Boolean> res;
        List<ProjectInfoRes> projectRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size);
            res = mypageService.getFeedbackProjectList(pageRequest, usersId);
            projectRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", projectRes);
        return Response.makeResponse(HttpStatus.OK, "피드백한 프로젝트 목록 조회 성공", projectRes.size(), resultMap);
    }

    @Operation(summary = "내가 즐겨찾기한 프로젝트 목록 조회 API")
    @GetMapping("/project/favorite")
    ResponseEntity<?> getFavoriteProjectList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                     @Parameter(description = "페이지 수")
                                     @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                     @RequestParam(defaultValue = "0") int page,
                                     @Parameter(description = "한 페이지에 담기는 개수")
                                     @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                     @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        Pair<List<ProjectInfoRes>, Boolean> res;
        List<ProjectInfoRes> projectRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size);
            res = mypageService.getFavoriteProjectList(pageRequest, usersId);
            projectRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", projectRes);
        return Response.makeResponse(HttpStatus.OK, "내 즐겨찾기 프로젝트 목록 조회 성공", projectRes.size(), resultMap);
    }

    @Operation(summary = "프로젝트 목록 조회 API")
    @GetMapping("/project")
    ResponseEntity<?> getProjectList(@CookieValue(value = JwtProperties.ACCESS_TOKEN, required = true) String accessToken,
                                     @Parameter(description = "페이지 수")
                                     @Min(value = 0, message = "page값은 0이상이어야 합니다")
                                     @RequestParam(defaultValue = "0") int page,
                                     @Parameter(description = "한 페이지에 담기는 개수")
                                     @Min(value = 1, message = "size값은 1이상이어야 합니다")
                                     @RequestParam(defaultValue = "10") int size) {
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();


        Pair<List<ProjectInfoRes>, Boolean> res;
        List<ProjectInfoRes> projectRes = new ArrayList<>();
        try{
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
            res = mypageService.getProjectList(pageRequest, usersId);
            projectRes = res.getLeft();
        } catch (Exception e){
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("nextPage", (res.getRight())? page + 1 : -1);
        resultMap.put("list", projectRes);
        return Response.makeResponse(HttpStatus.OK, "내 프로젝트 목록 조회 성공", projectRes.size(), resultMap);
    }

    @Operation(summary = "알림 조회 API")
    @GetMapping("/alarm")
    ResponseEntity<?> getAlarmList(@CookieValue(name = JwtProperties.ACCESS_TOKEN) String accessToken){
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        List<AlarmRes> res = new ArrayList<>();
        try {
            res = alarmService.getAlarmList(usersId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }
        return Response.makeResponse(HttpStatus.OK, "알림 조회 성공", res.size(), res);
    }

    @Operation(summary = "알림 삭제 API")
    @DeleteMapping("/alarm/{alarmId}")
    ResponseEntity<?> deleteAlarm(@CookieValue(name = JwtProperties.ACCESS_TOKEN) String accessToken,
                                   @Parameter(description = "알람 아이디")
                                   @ObjectId(message = "alarmId 형식이 일치하지 않습니다")
                                   @PathVariable String alarmId){
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        try {
            alarmService.deleteAlarm(alarmId, usersId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }
        return Response.ok("알림 삭제 성공");
    }

    @Operation(summary = "알림 전체 삭제 API")
    @DeleteMapping("/alarm")
    ResponseEntity<?> deleteAllAlarm(@CookieValue(name = JwtProperties.ACCESS_TOKEN) String accessToken){
        Long usersId = jwtTokenProvider.getId(accessToken);
        if(usersId <= 0)
            return Response.badRequestNoUser();

        try {
            alarmService.deleteAllAlarm(usersId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Response.badRequest(e.getMessage());
        }
        return Response.ok("알림 전체 삭제 성공");
    }
}
