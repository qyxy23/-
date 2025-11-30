package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiGameRepository extends JpaRepository<GameSession, String> {
}
