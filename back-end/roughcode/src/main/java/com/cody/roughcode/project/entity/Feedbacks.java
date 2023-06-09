package com.cody.roughcode.project.entity;

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
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "feedbacks")
public class Feedbacks extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedbacks_id", nullable = false, columnDefinition = "BIGINT")
    private Long feedbacksId;

    @Builder.Default
    @Column(name = "content", nullable = true, columnDefinition = "text")
    private String content = "";

    @Builder.Default
    @Column(name = "like_cnt", nullable = true)
    private int likeCnt = 0;

    @Builder.Default
    @Column(name = "selected", nullable = true)
    private int selected = 0;

    @Builder.Default
    @Column(name = "complained", nullable = true)
    private LocalDateTime complained = null;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", nullable = false)
    private ProjectsInfo projectsInfo;

    @Builder.Default
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = true)
    private Users users = null;

    @JsonManagedReference
    @OneToMany(mappedBy = "feedbacks")
    private List<FeedbacksLikes> feedbacksLikes;

    public void selectedUp() {
        this.selected += 1;
    }

    public void selectedDown() {
        this.selected -= 1;
    }

    public void editContent(String content) {
        this.content = content;
    }

    public void setComplained(){
        this.complained = LocalDateTime.now();
    }

    public void likeCntDown() {
        this.likeCnt -= 1;
    }
    public void likeCntUp() {
        this.likeCnt += 1;
    }
}
