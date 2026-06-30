package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.SubmitTheoryVO;

public interface TheorySubmissionService {

    SubmitTheoryVO submitTheory(String gameSessionId, String theory);

    /** 多人：全队投票通过后进入规则层裁定 */
    SubmitTheoryVO submitTheoryAfterTeamVote(String gameSessionId, String theory, Long submitterUserId);
}
