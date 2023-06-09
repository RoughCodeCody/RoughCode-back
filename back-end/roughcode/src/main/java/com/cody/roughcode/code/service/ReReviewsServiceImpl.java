package com.cody.roughcode.code.service;

import com.cody.roughcode.code.dto.req.ReReviewReq;
import com.cody.roughcode.code.dto.res.ReReviewRes;
import com.cody.roughcode.code.entity.ReReviewComplains;
import com.cody.roughcode.code.entity.ReReviewLikes;
import com.cody.roughcode.code.entity.ReReviews;
import com.cody.roughcode.code.entity.Reviews;
import com.cody.roughcode.code.repository.ReReviewComplainsRepository;
import com.cody.roughcode.code.repository.ReReviewLikesRepository;
import com.cody.roughcode.code.repository.ReReviewsRepository;
import com.cody.roughcode.code.repository.ReviewsRepository;
import com.cody.roughcode.exception.NotMatchException;
import com.cody.roughcode.project.entity.Feedbacks;
import com.cody.roughcode.project.entity.FeedbacksComplains;
import com.cody.roughcode.user.entity.Users;
import com.cody.roughcode.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReReviewsServiceImpl implements ReReviewsService {

    private final UsersRepository usersRepository;
    private final ReReviewsRepository reReviewsRepository;
    private final ReReviewComplainsRepository reReviewComplainsRepository;
    private final ReviewsRepository reviewsRepository;
    private final ReReviewLikesRepository reReviewLikesRepository;


    @Override
    @Transactional
    public int insertReReview(ReReviewReq req, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);

        Reviews reviews = reviewsRepository.findByReviewsId(req.getId());
        if(reviews == null) throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        if(reviews.getComplained() != null && reviews.getComplained().equals(true))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신고되어 삭제된 리뷰입니다");

        ReReviews savedReReview = reReviewsRepository.save(
                ReReviews.builder()
                        .users(users)
                        .reviews(reviews)
                        .content(req.getContent())
                        .build()
        );
        reviews.setReReviews(savedReReview);
        reviewsRepository.save(reviews);

        return reviews.getReReviews().size();
    }

    @Override
    @Transactional
    public int updateReReview(ReReviewReq req, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        ReReviews reReviews = reReviewsRepository.findByReReviewsId(req.getId());
        if(reReviews == null) throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        if(reReviews.getUsers() == null || !reReviews.getUsers().equals(users)) throw new NotMatchException();

        reReviews.setContent(req.getContent());
        reReviewsRepository.save(reReviews);

        return 1;
    }

    @Override
    @Transactional
    public List<ReReviewRes> getReReviewList(Long reviewsId, Long usersId) {
        Users user = usersRepository.findByUsersId(usersId);

        Reviews reviews = reviewsRepository.findByReviewsId(reviewsId);
        if(reviews == null) throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        List<ReReviews> reReviewsList = reReviewsRepository.findAllByReviewsId(reviewsId);

        List<ReReviewRes> reReviewResList = new ArrayList<>();
        if(reReviewsList != null)
            for (ReReviews r : reReviewsList) {
                ReReviewLikes reReviewLikes = (user != null)? reReviewLikesRepository.findByReReviewsAndUsers(r, user) : null;
                Boolean reReviewLiked = reReviewLikes != null;
                reReviewResList.add(ReReviewRes.toDto(r, reReviewLiked));
            }

        reReviewResList.sort((r1, r2) -> {
                    if (r1.getUserId().equals(usersId) && !r2.getUserId().equals(usersId)) {
                        return -1;
                    } else if (!r1.getUserId().equals(usersId) && r2.getUserId().equals(usersId)) {
                        return 1;
                    } else if(r1.getDate() != null && r2.getDate() != null) {
                        return r2.getDate().compareTo(r1.getDate());
                    }
                    return 1;
                });

        return reReviewResList;
    }

    @Override
    @Transactional
    public int deleteReReview(Long reReviewsId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        ReReviews reReviews = reReviewsRepository.findByReReviewsId(reReviewsId);
        if(reReviews == null) throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        if(reReviews.getUsers() == null || !reReviews.getUsers().equals(users)) throw new NotMatchException();

        List<ReReviewLikes> reReviewLikes = reReviewLikesRepository.findByReReviews(reReviews);
        reReviewLikesRepository.deleteAll(reReviewLikes);

        reReviewsRepository.delete(reReviews);

        return 1;
    }

    @Override
    @Transactional
    public int likeReReview(Long reReviewsId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");

        ReReviews reReviews = reReviewsRepository.findByReReviewsId(reReviewsId);
        if(reReviews == null) throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        // 이미 좋아요 한 리리뷰인지 확인
        ReReviewLikes reReviewLikes = reReviewLikesRepository.findByReReviewsAndUsers(reReviews, users);
        if(reReviewLikes != null) { // 리리뷰 좋아요 취소
            reReviewLikesRepository.delete(reReviewLikes);

            reReviews.likeCntDown();
            reReviewsRepository.save(reReviews);
            return 0;
        }
        else{ // 리리뷰 좋아요
            reReviewLikesRepository.save(ReReviewLikes.builder()
                    .reReviews(reReviews)
                    .users(users)
                    .build());

            reReviews.likeCntUp();
            reReviewsRepository.save(reReviews);
            return 1;
        }
    }


    @Override
    @Transactional
    public int reReviewComplain(Long reReviewsId, Long usersId) {
        Users users = usersRepository.findByUsersId(usersId);
        if(users == null) throw new NullPointerException("일치하는 유저가 존재하지 않습니다");
        ReReviews reReviews = reReviewsRepository.findByReReviewsId(reReviewsId);
        if(reReviews == null)
            throw new NullPointerException("일치하는 리뷰가 존재하지 않습니다");

        Reviews reviews = reviewsRepository.findByReReviews(reReviews);
        if(reviews.getComplained() != null && reviews.getComplained().equals(true))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "리-리뷰가 달린 리뷰가 이미 삭제되었습니다");

        if(reReviews.getUsers() != null && reReviews.getUsers().getUsersId().equals(users.getUsersId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "리뷰 작성자와 신고 유저가 동일합니다");

        if(reReviews.getComplained() != null && reReviews.getComplained().equals(true))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 삭제된 리뷰입니다");

        ReReviewComplains complains = reReviewComplainsRepository.findByReReviewsAndUsers(reReviews, users);
        if(complains != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 리뷰입니다");

        List<ReReviewComplains> complainList = reReviewComplainsRepository.findByReReviews(reReviews);

        log.info(complainList.size() + "번 신고된 리뷰입니다");

        ReReviewComplains newComplain = ReReviewComplains.builder()
                .reReviews(reReviews)
                .users(users)
                .build();

        reReviewComplainsRepository.save(newComplain);

        if(complainList.size() + 1 >= 5){
            reReviews.setComplained(true);
            reReviewsRepository.save(reReviews);
        }

        return 1;
    }
}
