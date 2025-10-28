package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGroupMessageReadId;
import com.guanyu.haigui.pojo.model.GroupMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 已读记录仓储（操作 chat_group_message_reads 表）
 */
@Repository
public interface ChatGroupMessageReadRepository extends JpaRepository<GroupMessageRead, ChatGroupMessageReadId> {
}