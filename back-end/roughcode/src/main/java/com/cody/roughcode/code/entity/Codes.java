package com.cody.roughcode.code.entity;

import com.cody.roughcode.code.dto.req.CodeReq;
import com.cody.roughcode.project.entity.Projects;
import com.cody.roughcode.user.entity.Users;
import com.cody.roughcode.util.BaseTimeEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "codes")
public class Codes extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codes_id", nullable = false, columnDefinition = "BIGINT")
    private Long codesId;

    @Column(name = "num", nullable = false)
    private Long num;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "title", nullable = false, length = 63)
    private String title;

    @Builder.Default
    @Column(name = "like_cnt", nullable = true)
    private int likeCnt = 0;

    @ManyToOne
    @JoinColumn(name = "code_writer_id", nullable = false)
    private Users codeWriter;

    @Builder.Default
    @Column(name = "review_cnt", nullable = true)
    private int reviewCnt = 0;

    @Builder.Default
    @Column(name = "expire_date", nullable = true)
    private LocalDateTime expireDate = null;

    @JsonManagedReference
    @OneToMany(mappedBy = "codes", fetch = FetchType.LAZY)
    private List<CodeSelectedTags> selectedTags;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "projects_id", nullable = true)
    private Projects projects;

    @JsonManagedReference
    @OneToMany(mappedBy = "codes", fetch = FetchType.LAZY)
    private List<Reviews> reviews;

    @JsonManagedReference
    @OneToMany(mappedBy = "codes", fetch = FetchType.LAZY)
    private List<CodeFavorites> codeFavorites;

    @JsonManagedReference
    @OneToMany(mappedBy = "codes", fetch = FetchType.LAZY)
    private List<CodeLikes> codeLikes;

    public void setProject(Projects project) {
        this.projects = project;
    }

    public void updateCode(CodeReq req) {
        this.title = req.getTitle();
    }

    public void likeCntUp() {
        this.likeCnt += 1;
    }

    public void likeCntDown() {
        this.likeCnt -= 1;
    }

    public void reviewCntUp() {
        this.reviewCnt += 1;
    }

    public void reviewCntDown() {
        this.reviewCnt -= 1;
    }

    public void updateTitle(String title){
        this.title = title;
    }

    public void setExpireDate() {
        this.expireDate = LocalDateTime.now().plusDays(30L);
    }
}
