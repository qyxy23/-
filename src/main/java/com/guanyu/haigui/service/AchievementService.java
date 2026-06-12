package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.vo.AchievementListVO;
import com.guanyu.haigui.pojo.vo.AchievementView;

import java.util.List;

public interface AchievementService {

    List<AchievementView> checkAfterQuestion(Long userId, String gameSessionId);

    List<AchievementView> evaluateSettlement(Long userId, GameSession session, String roomId,
                                             GameSettlementSnapshot snapshot);

    List<AchievementView> onManualGiveUp(Long userId, String gameSessionId);

    void onTheorySubmitted(Long userId, String gameSessionId);

    void onSoupUploaded(Long userId);

    void onSoupPublishApproved(Long uploaderId, String soupId);

    AchievementListVO listForUser(Long userId);

    List<AchievementView> listUnlockedInSession(Long userId, String gameSessionId);
}
