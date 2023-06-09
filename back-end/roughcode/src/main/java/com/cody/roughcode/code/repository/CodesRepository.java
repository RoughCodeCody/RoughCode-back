package com.cody.roughcode.code.repository;

import com.cody.roughcode.code.entity.Codes;
import com.cody.roughcode.project.entity.Projects;
import com.cody.roughcode.user.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CodesRepository extends JpaRepository<Codes, Long> {
    Codes findByCodesIdAndExpireDateIsNull(Long id);
    Codes findByCodesId(Long id);

//    // @Query 어노테이션은 LIMIT 설정 불가능 > Pageable 사용하여 가장 최신 버전의 프로젝트 불러오기 수행
//    @Query("SELECT c FROM Codes c WHERE c.num =" +
//            "(SELECT c2.num FROM Codes c2 WHERE c2.codesId = :id) ORDER BY c.version DESC")
//    List<Codes> findLatestCodesByCodesId(@Param("id") Long id, Pageable pageable);
//
//    default Codes findLatestByCodesId(Long codeId)
//    {
//        return findLatestCodesByCodesId(codeId, PageRequest.of(0, 1)).get(0);
//    }

    // @Query 어노테이션은 LIMIT 설정 불가능 > Pageable 사용하여 가장 최신 버전의 프로젝트 불러오기 수행
    @Query("SELECT c FROM Codes c WHERE c.num = " +
            "(SELECT c2.num FROM Codes c2 WHERE c2.codesId = :codeId and c2.codeWriter.usersId = :userId and c2.expireDate IS null ) "+
            "and c.codeWriter.usersId = :userId and c.expireDate is NULL ORDER BY c.version DESC")
    List<Codes> findLatestCodesByCodesIdAndUsersId(@Param("codeId") Long codeId, @Param("userId") Long userId, Pageable pageable);

    default Codes findLatestByCodesIdAndUsersId(Long codeId, Long userId)
    {
        // codeId, userId가 일치하는 코드가 없다면 null 반환
        if(findLatestCodesByCodesIdAndUsersId(codeId, userId, PageRequest.of(0, 1))== null || findLatestCodesByCodesIdAndUsersId(codeId, userId, PageRequest.of(0, 1)).size()==0){
            return null;
        }
        return findLatestCodesByCodesIdAndUsersId(codeId, userId, PageRequest.of(0, 1)).get(0);
    }

    List<Codes> findByNumAndCodeWriterAndExpireDateIsNullOrderByVersionDesc(Long num, Users codeWriter);

    @Query("SELECT c FROM Codes c WHERE c.version = (SELECT MAX(c2.version) FROM Codes c2 WHERE (c2.num = c.num AND c2.codeWriter = c.codeWriter and c2.expireDate is NULL )) " +
            "AND (LOWER(c.title) LIKE %:keyword% OR LOWER(c.codeWriter.name) LIKE %:keyword%) AND c.expireDate IS NULL")
    Page<Codes> findAllByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT c FROM Codes c JOIN CodesInfo ci ON c = ci.codes WHERE " +
            "ci.language.languagesId IN :langIds " +
            "AND (LOWER(c.title) LIKE %:keyword% OR LOWER(c.codeWriter.name) LIKE %:keyword%) " +
            "AND c.expireDate is NULL ")
    Page<Codes> findAllByKeywordAndLanguage(@Param("keyword") String keyword, @Param("langIds") List<Long> langIds, Pageable pageable);

    @Query("SELECT c FROM Codes c WHERE c.codeWriter.usersId = :userId AND c.version = (SELECT MAX(c2.version) FROM Codes c2 WHERE (c2.num = c.num AND c2.codeWriter = c.codeWriter AND c.expireDate IS null )) AND c.expireDate IS null ")
    Page<Codes> findAllByCodeWriter(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT cf.codes FROM CodeFavorites cf JOIN cf.codes c WHERE cf.users.usersId = :userId AND cf.codes.expireDate is NULL ORDER BY c.createdDate DESC")
    Page<Codes> findAllMyFavorite(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT distinct r.codes FROM Reviews r JOIN r.codes c WHERE r.users.usersId = :userId AND r.codes.expireDate is NULL ORDER BY r.codes.createdDate DESC")
    Page<Codes> findAllMyReviews(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT count(c) FROM Codes c WHERE c.codeWriter = :user AND c.version > 1 AND c.expireDate IS NULL")
    int countByCodeWriter(@Param("user") Users user);

    List<Codes> findByProjects(Projects projects);

    List<Codes> findByExpireDateBefore(LocalDateTime currentDate);

}
