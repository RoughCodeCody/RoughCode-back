package com.cody.roughcode.project.repository;

import com.cody.roughcode.project.entity.ProjectTags;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTagsRepository extends JpaRepository<ProjectTags, Long> {
    ProjectTags findByTagsId(Long id);
    List<ProjectTags> findAllByNameContaining(String name, Sort sort);
}
