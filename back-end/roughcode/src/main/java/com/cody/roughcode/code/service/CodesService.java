package com.cody.roughcode.code.service;

import com.cody.roughcode.code.dto.req.CodeReq;
import com.cody.roughcode.code.dto.res.*;
import org.springframework.data.domain.PageRequest;

import javax.mail.MessagingException;
import java.util.List;

public interface CodesService {

    List<CodeInfoRes> getCodeList(String sort, PageRequest pageRequest, String keyword, String tagIdList, Long userId);

    Long insertCode(CodeReq req, Long userId) throws MessagingException;

    CodeDetailRes getCode(Long codeId, Long userId);

    int updateCode(CodeReq req, Long codeId, Long userId);

    int putExpireDateCode(Long codeId, Long userId);

    void deleteExpiredCode();

    int likeCode(Long codeId, Long userId);

    int favoriteCode(Long codeId, String content, Long userId);

    List<CodeTagsRes> searchTags(String keyword);

    List<ReviewInfoRes> getReviewList(Long codeId, Long userId, boolean versionUp);

    List<ReviewSearchRes> getReviewSearchList(Long codeId, Long userId, String keyword);

    int connectProject(Long codeId, Long userId, Long projectId);

    List<CodeLanguagesRes> searchLanguages(String keyword);
}
