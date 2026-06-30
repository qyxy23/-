package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.TheoryDraftVO;
import com.guanyu.haigui.pojo.vo.VoteEndGameVO;

public interface TheoryDraftService {

    TheoryDraftVO getDraftState(String roomId, Long userId);

    TheoryDraftVO acquireLock(String roomId, Long userId);

    TheoryDraftVO releaseLock(String roomId, Long userId);

    TheoryDraftVO saveDraft(String roomId, Long userId, String draftText);

    VoteEndGameVO submitDraftForVote(String roomId, Long userId, String draftText);

    TheoryDraftVO getDraftStateBySession(String gameSessionId, Long userId, boolean voting);
}
