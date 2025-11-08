package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> { // 注意：主键是msgId（Long），原接口写错了！

    /**
     * 自定义插入消息（关联会话的sessionId，避免JPA自动管理关联）
     * 
     * @param msg 消息实体
     */
    @Modifying
    @Query(value = """
                INSERT INTO ai_chat_messages
                (session_id, sender_type, content, send_time, is_read, sender_id)
                VALUES
                (:#{#msg.chatSession.sessionId}, :#{#msg.role.name()}, :#{#msg.content},
                 :#{#msg.sendTime}, :#{#msg.isRead}, :#{#msg.senderId})
            """, nativeQuery = true)
    void insertMsg(AiChatMessage msg);

}