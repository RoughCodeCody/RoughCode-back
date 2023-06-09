package com.cody.roughcode.project.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInfoRes {
    @Schema(description = "프로젝트 아이디", example = "1")
    private Long projectId;

    @Schema(description = "버전", example = "1")
    private int version;

    @Schema(description = "프로젝트 이름", example = "개발새발")
    private String title;

    @Schema(description = "날짜 및 시간", example = "2023-04-24 16:33")
    private LocalDateTime date;

    @Schema(description = "좋아요수", example = "1")
    private int likeCnt;

    @Builder.Default
    @Schema(description = "좋아요 여부", example = "1")
    private boolean liked = false;

    @Schema(description = "피드백수", example = "2")
    private int feedbackCnt;

    @Schema(description = "썸네일 url", example = "https://d2swdwg2kwda2j.cloudfront.net/project/kosy318_1_5")
    private String img;

    @Schema(description = "태그 이름 리스트", example = "[springboot, react]")
    private List<ProjectTagsRes> tags;

    @Schema(description = "한줄 설명", example = "개발새발 프로젝트입니다")
    private String introduction;

    @Schema(description = "닫힘 여부", example = "false")
    private Boolean closed;
}
