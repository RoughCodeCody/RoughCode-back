package com.cody.roughcode.config;

import com.cody.roughcode.security.auth.JwtAuthenticationFilter;
import com.cody.roughcode.security.auth.JwtTokenProvider;
import com.cody.roughcode.security.handler.AuthenticationFailureHandler;
import com.cody.roughcode.security.handler.AuthenticationSuccessHandler;
import com.cody.roughcode.security.handler.CustomLogoutHandler;
import com.cody.roughcode.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.cody.roughcode.security.oauth2.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final CorsConfig corsConfig;
    private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;
    private final AuthenticationSuccessHandler authenticationSuccessHandler;
    private final AuthenticationFailureHandler authenticationFailureHandler;

    private final CustomLogoutHandler customLogoutHandler;
    private final String URL_PREFIX = "/api/v1";

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .antMatchers(HttpMethod.GET, "/api/v1/project")
                .antMatchers(HttpMethod.GET, "/api/v1/project/{projectId}")
                .antMatchers(HttpMethod.PUT, "/api/v1/project/check/{projectId}")
                .antMatchers(HttpMethod.GET, "/api/v1/code") // 코드 목록 조회
                .antMatchers(HttpMethod.GET, "/api/v1/code/{codeId}") // 코드 정보 상세 조회
                .antMatchers(HttpMethod.GET, "/api/v1/code/{codeId}/code-review") // 코드에 대한 리뷰 목록 검색 - 상세 조회에 사용
                .antMatchers(HttpMethod.POST, "/api/v1/code/review") // 코드에 대한 리뷰 등록
                .antMatchers(HttpMethod.GET, "/api/v1/code/review/{reviewId}") // 코드 리뷰 상세 조회
                .antMatchers(HttpMethod.GET, "/api/v1/code/rereview/{reviewId}") // 코드 리뷰에 대한 리뷰 목록 조회
                .antMatchers(HttpMethod.POST, "/api/v1/code/rereview") // 코드 리뷰에 대한 리뷰 등록
                .antMatchers(HttpMethod.GET, "/api/v1/code/tag") // 코드 태그 검색
                .antMatchers(HttpMethod.GET, "/api/v1/code/language") // 코드 언어 검색
                .antMatchers(HttpMethod.GET, "/api/v1/mypage") // stat card 조회
                .antMatchers(HttpMethod.POST, "/api/v1/project/feedback") // 프로젝트 피드백 작성
                .antMatchers(HttpMethod.POST, "/api/v1/project/{projectId}/image") // 프로젝트 이미지 to url
                .antMatchers(HttpMethod.GET, "/api/v1/project/tag") // 프로젝트 tag 검색
                .antMatchers(URL_PREFIX+"/user/token", "/swagger-ui/**", "/swagger-resources/**", "/v2/api-docs/**", "/v3/api-docs/**", "/favicon.ico");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService) throws Exception {
        http
                .httpBasic().disable() // 기본 로그인 화면 비활성화
                .formLogin().disable() // 폼로그인 비활성화
                .csrf().disable()   // csrf 보안 비활성화
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // jwt 사용으로 session 비활성화
                .and()
                .logout()
                .logoutUrl(URL_PREFIX+"/user/logout") // 로그아웃 처리 URL
//                .logoutSuccessUrl("/login") // 로그아웃 성공후 이동할 페이지
                .deleteCookies("accessToken", "refreshToken") // 쿠키 삭제
                .addLogoutHandler(customLogoutHandler)// 로그아웃 구현할 class 넣기
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_OK);
                })
                .and()
                .authorizeRequests()
                .anyRequest().permitAll()//authenticated() // 인가 검증
                .and()
                .oauth2Login()
                .authorizationEndpoint(authorize -> {
                    // 프론트엔드에서 백엔드로 소셜로그인 요청을 보내는 URI
                    authorize.baseUri(URL_PREFIX+"/oauth2/authorization");
                    // Authorization 과정에서 기본으로 Session을 사용하지만 Cookie로 변경하기 위해 설정함
                    authorize.authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository);
                })
                .redirectionEndpoint(redirectionEndpoint ->
                    redirectionEndpoint
                            .baseUri(URL_PREFIX+"/login/oauth2/code/*")
                )
                .userInfoEndpoint(userInfo -> { // Provider로부터 획득한 유저정보를 다룰 service class 지정함
                    userInfo.userService(customOAuth2UserService);
                })
                .successHandler(authenticationSuccessHandler) // OAuth2 로그인 성공시 호출할 handler
                .failureHandler(authenticationFailureHandler) // OAuth2 로그인 실패시 호출할 handler
                .and()
                .addFilter(corsConfig.corsFilter()) // cors 설정. 일단 전부 풀어놓음
                // 모든 request에서 JWT를 검사할 filter를 추가함
                //      UsernamePasswordAuthenticationFilter에서 클라이언트가 요청한 리소스의 접근권한이 없을 때 막는 역할을 하기 때문에
                //      이 필터 전에 jwtAuthenticationFilter 실행
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate), UsernamePasswordAuthenticationFilter.class);
//                .addFilterBefore(new JwtExceptionFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }
}
