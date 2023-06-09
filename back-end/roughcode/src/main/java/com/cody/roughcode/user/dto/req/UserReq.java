package com.cody.roughcode.user.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReq {

    @Schema(description = "사용자 닉네임", example = "cody306")
    private String nickname;

    @Schema(description = "사용자 이메일(이메일이 없는 경우 빈 문자열)", example = "cody306@ssafy.com")
    private String email;

}