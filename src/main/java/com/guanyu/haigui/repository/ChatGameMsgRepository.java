package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGameMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatGameMsgRepository extends
        JpaRepository<ChatGameMessage, String>,
        JpaSpecificationExecutor<ChatGameMessage> {

    /**
     * 方法1：查询指定群房间 ID 的所有消息（带关联对象，避免懒加载）
     * @param roomId 群房间 ID
     * @param pageable 分页/排序参数
     * @return 分页消息
     */
    @Query("SELECT m FROM ChatGameMessage m " +
            "LEFT JOIN FETCH m.chatGame " +  // 左连接预加载群房间（避免 N+1 问题）
            "LEFT JOIN FETCH m.sender " +   // 左连接预加载发送者
            "WHERE m.chatGame.roomId = :roomId")
    Page<ChatGameMessage> findByRoomIdWithAssociations(
            @Param("roomId") String roomId,
            Pageable pageable
    );

    /**
     * 方法2：查询指定群房间 ID 的最新消息（按创建时间倒序）
     * 利用 Spring Data JPA 命名规则自动生成 SQL：
     * - findByChatGame_RoomId：关联 ChatGame 的 roomId 字段
     * - OrderByCreateTimeDesc：按 createTime 字段倒序
     */
    Page<ChatGameMessage> findByChatGame_RoomIdOrderByCreateTimeDesc(
            String roomId,
            Pageable pageable
    );

    @Query("SELECT m FROM ChatGameMessage m " +
            "LEFT JOIN FETCH m.sender " +
            "WHERE m.chatGame.roomId = :roomId " +
            "ORDER BY m.createTime ASC")
    List<ChatGameMessage> findByChatGame_RoomIdOrderByCreateTimeAsc(@Param("roomId") String roomId);

    /** 对局结束并写入复盘缓存后，清理大厅原始聊天记录 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChatGameMessage m WHERE m.chatGame.roomId = :roomId")
    int deleteByChatGameRoomId(@Param("roomId") String roomId);
}