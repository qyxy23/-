package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 群聊消息仓储（操作 chat_group_messages 表）
 */
@Repository
public interface ChatGroupMessageRepository extends JpaRepository<GroupMessage, String> {
}