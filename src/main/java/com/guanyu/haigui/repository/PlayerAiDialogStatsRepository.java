package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.PlayerAiDialogStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerAiDialogStatsRepository extends JpaRepository<PlayerAiDialogStats,String> {

    Optional<PlayerAiDialogStats> findByGameSessionSessionIdAndUserUserId(String sessionId, Long userId);

}
