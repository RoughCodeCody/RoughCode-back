package com.cody.roughcode.code.repository;

import com.cody.roughcode.code.entity.*;
import com.cody.roughcode.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReReviewLikesRepository extends JpaRepository<ReReviewLikes, Long> {

    ReReviewLikes findByReReviewsAndUsers(ReReviews reReview, Users user);

    List<ReReviewLikes> findByReReviews(ReReviews reReviews);

    @Modifying
    @Transactional
    @Query("delete from ReReviewLikes r "+
            "where r.reReviews.reReviewsId in "+
            "(select rr.reReviewsId from ReReviews rr where rr.reviews.reviewsId in "+
            "(select rv.reviewsId from Reviews rv where rv.codes.codesId = :codesId))")
    void deleteAllByCodesId(@Param("codesId") Long codesId);

    @Modifying
    @Transactional
    @Query("delete from ReReviewLikes r "+
            "where r.reReviews.reReviewsId in "+
            "(select rr.reReviewsId from ReReviews rr where rr.reviews.reviewsId = :reviewsId)")
    void deleteAllByReviewId(@Param("reviewsId") Long reviewsId);

    @Modifying
    @Transactional
    @Query("delete from ReReviewLikes r "+
            "where r.reReviews.reReviewsId in "+
            "(select rr.reReviewsId from ReReviews rr where rr.reviews.reviewsId in "+
            "(select rv.reviewsId from Reviews rv where rv.codes in :codesList))")
    void deleteAllByCodesList(@Param("codesList") List<Codes> codesList);
}
