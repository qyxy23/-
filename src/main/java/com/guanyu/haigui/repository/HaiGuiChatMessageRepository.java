package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiChatMessageRepository extends JpaRepository<HaiGuiChatMessage, Long> {
}
