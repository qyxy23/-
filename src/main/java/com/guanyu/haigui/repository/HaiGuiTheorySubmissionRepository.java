package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiTheorySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiTheorySubmissionRepository extends JpaRepository<HaiGuiTheorySubmission, Long> {

    long countByGameSessionIdAndFormalAttemptTrue(String gameSessionId);
}
