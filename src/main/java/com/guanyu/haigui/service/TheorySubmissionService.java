package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.SubmitTheoryVO;

public interface TheorySubmissionService {

    SubmitTheoryVO submitTheory(String gameSessionId, String theory);
}
