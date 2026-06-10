package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiChatMessageWithFragments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HaiGuiChatMessageRepository extends JpaRepository<HaiGuiChatMessageWithFragments, Long> {
    List<HaiGuiChatMessageWithFragments> findAllByGameSessionId(String gameSessionId);

    List<HaiGuiChatMessageWithFragments> findAllByGameSessionIdOrderByCreatedAtAsc(String gameSessionId);
}
