package com.cody.roughcode.project.repository;

import com.cody.roughcode.project.entity.ProjectFavorites;
import com.cody.roughcode.project.entity.Projects;
import com.cody.roughcode.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProjectFavoritesRepository extends JpaRepository<ProjectFavorites, Long> {
    ProjectFavorites findByProjectsAndUsers(Projects project, Users user);

    @Query("SELECT pf.users FROM ProjectFavorites pf WHERE pf.projects = :original AND pf.projects.expireDate IS NULL")
    List<Users> findByProjects(@Param("original") Projects original);

    @Modifying
    @Transactional
    @Query("delete from ProjectFavorites pf where pf.projects IN :projectsList")
    void deleteAllByProjectsList(@Param("projectsList") List<Projects> projectsList);

    // Querydsl로 변경
//    @Query("SELECT pf.users FROM ProjectFavorites pf WHERE pf.projects = :original AND pf.projects.expireDate IS NULL")
//    List<Users> findByProjects(@Param("original") Projects original);

}
