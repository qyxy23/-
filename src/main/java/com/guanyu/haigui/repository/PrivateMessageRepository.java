package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Optional;

/**
 * 私聊消息仓库（Spring Data JPA 自动生成基础CRUD，以下为业务定制方法）
 */
@Repository
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, String> {

    // 查找两个用户之间的最后一条消息
    @Query("SELECT m FROM PrivateMessage m WHERE (m.sender.userId = :userId1 AND m.receiver.userId = :userId2) OR (m.sender.userId = :userId2 AND m.receiver.userId = :userId1) ORDER BY m.createTime DESC")
    Optional<PrivateMessage> findLastMessageBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // 分页查询两个用户之间的历史消息
    @Query("SELECT m FROM PrivateMessage m WHERE (m.sender.userId = :userId1 AND m.receiver.userId = :userId2) OR (m.sender.userId = :userId2 AND m.receiver.userId = :userId1) ORDER BY m.createTime DESC")
    Page<PrivateMessage> findHistoryMessagesBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2, Pageable pageable);

    // -------------------------- 基础查询：按用户/对话查找 --------------------------
    /**
     * 根据【发送者+接收者】查找消息（分页，按时间倒序）
     * 场景：查看与某个人的所有聊天记录
     */
    Page<PrivateMessage> findBySenderAndReceiverOrderByCreateTimeDesc(
            UserInfo sender,
            UserInfo receiver, 
            Pageable pageable
    );

    /**
     * 查找【两个用户之间的完整对话】（不管谁发谁，合并成一条对话流）
     * 场景：加载两人聊天界面，按时间排序所有消息
     */
    @Query("SELECT pm FROM PrivateMessage pm " +
           "WHERE (pm.sender = :user1 AND pm.receiver = :user2) " +
           "   OR (pm.sender = :user2 AND pm.receiver = :user1) " +
           "ORDER BY pm.createTime DESC")
    Page<PrivateMessage> findConversationBetweenUsers(
            @Param("user1") UserInfo user1, 
            @Param("user2") UserInfo user2, 
            Pageable pageable
    );

    // -------------------------- 分页查询：按发送者/接收者 --------------------------
    /**
     * 根据【发送者】查找所有消息（分页，按时间倒序）
     * 场景：查看自己发送的所有私聊消息
     */
    Page<PrivateMessage> findBySenderOrderByCreateTimeDesc(UserInfo sender, Pageable pageable);

    /**
     * 根据【接收者】查找所有消息（分页，按时间倒序）
     * 场景：查看别人发给自己的所有私聊消息
     */
    Page<PrivateMessage> findByReceiverOrderByCreateTimeDesc(UserInfo receiver, Pageable pageable);

    Optional<PrivateMessage> findByMessageId(String messageId);

    Optional<PrivateMessage> findBySender_UserIdAndReceiver_UserIdAndClientMsgId(
            Long senderId,
            Long receiverId,
            String clientMsgId);

    /** 增量同步：两用户会话中某时间点之后的消息（升序） */
    @Query("""
            SELECT m FROM PrivateMessage m
            WHERE ((m.sender.userId = :userId1 AND m.receiver.userId = :userId2)
                OR (m.sender.userId = :userId2 AND m.receiver.userId = :userId1))
              AND m.createTime > :afterTime
            ORDER BY m.createTime ASC
            """)
    List<PrivateMessage> findMessagesAfterBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2,
            @Param("afterTime") LocalDateTime afterTime,
            Pageable pageable);

    /** 硬删除两用户之间的全部私聊消息 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM PrivateMessage pm
            WHERE (pm.sender.userId = :userId1 AND pm.receiver.userId = :userId2)
               OR (pm.sender.userId = :userId2 AND pm.receiver.userId = :userId1)
            """)
    void deleteAllBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /** 删除早于 cutoff 的私聊消息（全局时间窗） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM chat_private_messages WHERE create_time < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 每个私聊对仅保留最新 maxMessages 条（条数滑动窗）
     * 使用 LEAST/GREATEST 归一化会话对，避免 A-B 与 B-A 被当成两个会话
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE pm FROM chat_private_messages pm
            INNER JOIN (
                SELECT message_id FROM (
                    SELECT message_id,
                           ROW_NUMBER() OVER (
                               PARTITION BY LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)
                               ORDER BY create_time DESC, message_id DESC
                           ) AS rn
                    FROM chat_private_messages
                ) ranked
                WHERE rn > :maxMessages
            ) doomed ON pm.message_id = doomed.message_id
            """, nativeQuery = true)
    int deleteExceedingMaxPerConversation(@Param("maxMessages") int maxMessages);

    /** 单会话发送后裁剪：删除最新 maxMessages 条之外的旧消息 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM chat_private_messages
            WHERE message_id IN (
                SELECT message_id FROM (
                    SELECT message_id FROM chat_private_messages
                    WHERE (sender_id = :minUserId AND receiver_id = :maxUserId)
                       OR (sender_id = :maxUserId AND receiver_id = :minUserId)
                    ORDER BY create_time ASC, message_id ASC
                    LIMIT 1000000 OFFSET :maxMessages
                ) overflow
            )
            """, nativeQuery = true)
    int trimConversationBeyondMax(
            @Param("minUserId") long minUserId,
            @Param("maxUserId") long maxUserId,
            @Param("maxMessages") int maxMessages);
}