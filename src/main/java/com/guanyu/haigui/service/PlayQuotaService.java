package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.GrantPlayQuotaDTO;
import com.guanyu.haigui.pojo.dto.QueryPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.ReviewPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.dto.SubmitPlayAccessRequestDTO;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestListVO;
import com.guanyu.haigui.pojo.vo.PlayAccessRequestVO;
import com.guanyu.haigui.pojo.vo.PlayQuotaSummaryVO;

import java.util.List;

public interface PlayQuotaService {

    void initForNewUser(Long userId);

    PlayQuotaSummaryVO getMySummary();

    void assertCanStartNewGame(Long userId);

    void chargeOnSettlement(String gameSessionId);

    PlayAccessRequestVO submitAccessRequest(SubmitPlayAccessRequestDTO dto);

    List<PlayAccessRequestVO> listMyRequests();

    PlayAccessRequestListVO listAccessRequestsForReview(QueryPlayAccessRequestDTO dto);

    PlayAccessRequestVO reviewAccessRequest(ReviewPlayAccessRequestDTO dto);

    void adminGrantQuota(GrantPlayQuotaDTO dto);
}
