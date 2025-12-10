package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiVoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HaiGuiVoteRecordRepository extends JpaRepository<HaiGuiVoteRecord, Long> {
    boolean existsByVoteSessionIdAndUserId(String voteSessionId, Long currentUserId);

    List<HaiGuiVoteRecord> findByVoteSessionId(String voteSessionId);
}
