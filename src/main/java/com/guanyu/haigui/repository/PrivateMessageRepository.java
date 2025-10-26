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

import java.util.List;
import java.util.Optional;

/**
 * 私聊消息仓库（Spring Data JPA 自动生成基础CRUD，以下为业务定制方法）
 */
@Repository
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, String> {

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

    // -------------------------- 未读消息查询 --------------------------
    /**
     * 根据【接收者】查找未读消息（按时间倒序）
     * 场景：获取当前用户的未读私聊列表
     */
    List<PrivateMessage> findByReceiverAndIsReadFalseOrderByCreateTimeDesc(UserInfo receiver);

    /**
     * 统计【接收者】的未读消息数量
     * 场景：显示未读消息角标（如小红点）
     */
    long countByReceiverAndIsReadFalse(UserInfo receiver);

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

    // -------------------------- 辅助操作：标记已读 --------------------------
    /**
     * 根据消息ID标记为已读（批量或单条）
     * 场景：用户打开聊天界面时，更新未读消息状态
     */
    @Modifying // 声明该方法会修改数据库
    @Query("UPDATE PrivateMessage pm SET pm.isRead = true WHERE pm.messageId IN :messageIds")
    void markMessagesAsRead(@Param("messageIds") List<String> messageIds);

    // -------------------------- 可选：按消息ID查找 --------------------------
    Optional<PrivateMessage> findByMessageId(String messageId);
}