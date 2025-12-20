package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, String> {


    /**
     * 自定义更新会话（根据sessionId更新标题和更新时间）
     * @param session 会话实体（需包含sessionId）
     */
    @Modifying
    @Query(value = """
        UPDATE ai_chat_sessions
        SET title = :#{#session.title}
        WHERE session_id = :#{#session.sessionId}
    """, nativeQuery = true)
    void updateBySessionId(AiChatSession session);


    /**
     * 自定义逻辑删除：将会话标记为已删除（is_deleted = 1）
     * @param sessionId 会话ID（对应数据库 session_id 列）
     */
    @Modifying
    @Query(value = """
        UPDATE ai_chat_sessions
        SET is_deleted = 1
        WHERE session_id = ?1
    """, nativeQuery = true)
    void deleteBySessionId(String sessionId); // 逻辑删除的专用方法（重命名后避免混淆）

    /**
     * 自定义查询：根据 sessionId 查找会话实体
     * @param sessionId 会话ID（对应数据库 session_id 列）
     * @return 匹配的会话实体，未找到返回 null
     */
    @Query(value = """
        SELECT *
        FROM ai_chat_sessions
        WHERE session_id = ?1
    """, nativeQuery = true)
    AiChatSession selectSessionBySessionId(String sessionId);

}