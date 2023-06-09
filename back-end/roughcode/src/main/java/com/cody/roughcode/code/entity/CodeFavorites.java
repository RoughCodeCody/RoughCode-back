package com.cody.roughcode.code.entity;

import com.cody.roughcode.user.entity.Users;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "code_favorites")
public class CodeFavorites {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorites_id", nullable = false, columnDefinition = "BIGINT")
    private Long favoritesId;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codes_id", nullable = false)
    private Codes codes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private Users users;

    @Builder.Default
    @Column(name = "content", nullable = true, columnDefinition = "text")
    private String content = "";
}
