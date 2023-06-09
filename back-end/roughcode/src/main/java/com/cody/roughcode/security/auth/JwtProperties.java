package com.cody.roughcode.security.auth;

//@Configuration

public interface JwtProperties {
    String TOKEN_HEADER = "Authorization";
    int test = 2 * 24; // 테스트를 위한 추가 시간
    int ACCESS_TOKEN_TIME = test* 30 * 1000 * 60; // 24시간 (테스트용)
//    int ACCESS_TOKEN_TIME = 30 * 1000 * 60; // 30분
    int REFRESH_TOKEN_TIME = 7 * 24 * 60 * 60 * 1000; // 7일
    String AUTHORITIES_KEY = "auth";
    String REFRESH_TOKEN = "refreshToken";
    String ACCESS_TOKEN = "accessToken";
}

