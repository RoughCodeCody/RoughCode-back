package com.cody.roughcode.project.repository;

import com.cody.roughcode.project.entity.ProjectSelectedTags;
import com.cody.roughcode.project.entity.Projects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProjectSelectedTagsRepository extends JpaRepository<ProjectSelectedTags, Long> {
    @Query("SELECT p FROM Projects p JOIN p.selectedTags pst WHERE " +
            "p.version = (SELECT MAX(p2.version) FROM Projects p2 WHERE (p2.num = p.num AND p2.projectWriter = p.projectWriter AND p.expireDate IS NULL)) AND " +
            "pst.tags.tagsId IN :tagIds " +
            "AND (LOWER(p.title) LIKE %:keyword% OR LOWER(p.introduction) LIKE %:keyword%) " +
            "GROUP BY p.projectsId " +
            "HAVING COUNT(DISTINCT pst.tags.tagsId) = :tagIdsSize"
    )
    Page<Projects> findAllByKeywordAndTag(@Param("keyword") String keyword, @Param("tagIds") List<Long> tagIds, @Param("tagIdsSize") Long tagIdsSize, Pageable pageable);

    @Query("SELECT p FROM Projects p JOIN p.selectedTags pst WHERE " +
            "p.version = (SELECT MAX(p2.version) FROM Projects p2 WHERE (p2.num = p.num AND p2.projectWriter = p.projectWriter AND p.expireDate IS NULL)) AND " +
            "pst.tags.tagsId IN :tagIds " +
            "AND p.closed = false " +
            "AND (LOWER(p.title) LIKE %:keyword% OR LOWER(p.introduction) LIKE %:keyword%) " +
            "GROUP BY p.projectsId " +
            "HAVING COUNT(DISTINCT pst.tags.tagsId) = :tagIdsSize"
    )
    Page<Projects> findAllOpenedByKeywordAndTag(@Param("keyword") String keyword, @Param("tagIds") List<Long> tagIds, @Param("tagIdsSize") Long tagIdsSize, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from ProjectSelectedTags pst where pst.projects IN :projectsList")
    void deleteAllByProjectsList(@Param("projectsList") List<Projects> projectsList);
}
