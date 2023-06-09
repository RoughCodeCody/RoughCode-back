package com.cody.roughcode.code.entity;

import com.cody.roughcode.code.dto.req.CodeReq;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "codes_info")
public class CodesInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long id;

    @Builder.Default
    @Column(name = "github_url", nullable = false)
    private String githubUrl = "";

    @Builder.Default
    @Column(name = "github_api_url", nullable = false)
    private String githubApiUrl = "";

    @Builder.Default
    @Column(name = "content", nullable = true, columnDefinition = "text")
    private String content = "";

    @Builder.Default
    @Column(name = "favorite_cnt", nullable = true)
    private int favoriteCnt = 0;

//    @Builder.Default
//    @Column(name = "language", nullable = true)
//    private String language = "";

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "languages_id", nullable = false)
    private CodeLanguages language;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codes_id", nullable = false)
    private Codes codes;

    @JsonManagedReference
    @OneToMany(mappedBy = "codesInfo", fetch = FetchType.LAZY)
    private List<SelectedReviews> selectedReviews;

    public void updateCode(CodeReq req) {
        this.content = req.getContent();
        this.githubUrl = req.getGithubUrl();
    }

    public void favoriteCntUp() {
        this.favoriteCnt += 1;
    }

    public void favoriteCntDown() {
        this.favoriteCnt -= 1;
    }

    public void updateContent(String content){
        this.content = content;
    }

    public void updateGithubUrl(String githubUrl){
        this.githubUrl = githubUrl;
    }

    public void updateGithubApiUrl(String githubApiUrl){
        this.githubApiUrl = githubApiUrl;
    }

    public void updateLanguage(CodeLanguages language){
        this.language = language;
    }
}
