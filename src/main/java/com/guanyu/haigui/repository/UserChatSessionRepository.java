package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.UserChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserChatSessionRepository extends JpaRepository<UserChatSession, Long> {

    Optional<UserChatSession> findByUserIdAndSessionIdAndChatType(Long userId, String sessionId, String chatType);

    List<UserChatSession> findByUserIdAndIsStickyTrueOrderByLastMessageTimeDescIdDesc(Long userId);

    @Query("""
            SELECT s FROM UserChatSession s
            WHERE s.userId = :userId AND s.isSticky = false
            ORDER BY s.lastMessageTime DESC, s.id DESC
            """)
    List<UserChatSession> findNonStickyFirstPage(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT s FROM UserChatSession s
            WHERE s.userId = :userId AND s.isSticky = false
              AND (
                s.lastMessageTime < :cursorTime
                OR (s.lastMessageTime = :cursorTime AND s.id < :cursorId)
                OR (s.lastMessageTime IS NULL AND :cursorTime IS NOT NULL)
              )
            ORDER BY s.lastMessageTime DESC, s.id DESC
            """)
    List<UserChatSession> findNonStickyAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    List<UserChatSession> findBySessionIdAndChatType(String sessionId, String chatType);

    List<UserChatSession> findByUserIdAndChatTypeAndSessionIdIn(
            Long userId, String chatType, Collection<String> sessionIds);

    @Modifying
    @Query("""
            UPDATE UserChatSession s
            SET s.lastMessageContent = :content,
                s.lastMessageTime = :time,
                s.lastSenderName = :senderName
            WHERE s.sessionId = :groupId AND s.chatType = 'GROUP'
            """)
    void updateGroupLastMessageForAll(
            @Param("groupId") String groupId,
            @Param("content") String content,
            @Param("time") LocalDateTime time,
            @Param("senderName") String senderName);

    @Modifying
    @Query("""
            DELETE FROM UserChatSession s
            WHERE s.userId = :userId AND s.sessionId = :sessionId AND s.chatType = :chatType
            """)
    void deleteByUserAndSession(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("chatType") String chatType);

    @Modifying
    @Query("""
            DELETE FROM UserChatSession s
            WHERE s.sessionId = :sessionId AND s.chatType = 'GROUP'
            """)
    void deleteAllGroupSessions(@Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            UPDATE UserChatSession s
            SET s.unreadCount = s.unreadCount + 1
            WHERE s.sessionId = :groupId AND s.chatType = 'GROUP' AND s.userId <> :excludeUserId
            """)
    void incrementGroupUnreadExcept(@Param("groupId") String groupId, @Param("excludeUserId") Long excludeUserId);

    @Modifying
    @Query("""
            UPDATE UserChatSession s
            SET s.chatName = :chatName
            WHERE s.sessionId = :groupId AND s.chatType = 'GROUP'
            """)
    void updateGroupName(@Param("groupId") String groupId, @Param("chatName") String chatName);

    @Modifying
    @Query("""
            UPDATE UserChatSession s
            SET s.chatAvatar = :chatAvatar
            WHERE s.sessionId = :groupId AND s.chatType = 'GROUP'
            """)
    void updateGroupAvatar(@Param("groupId") String groupId, @Param("chatAvatar") String chatAvatar);
}
