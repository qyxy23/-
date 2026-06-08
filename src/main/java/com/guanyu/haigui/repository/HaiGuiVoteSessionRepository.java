package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiVoteSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HaiGuiVoteSessionRepository extends JpaRepository<HaiGuiVoteSession, String> {
    List<HaiGuiVoteSession> findBySessionIdAndStatusOrderByCreatedAtDesc(String SessionId, HaiGuiVoteSession.VoteStatus voteStatus);

    List<HaiGuiVoteSession> findByStatusAndEndTimeBefore(HaiGuiVoteSession.VoteStatus status, LocalDateTime endTime);
}
