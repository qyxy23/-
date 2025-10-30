package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户信息仓库（Spring Data JPA 自动生成实现）
 */
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    // 根据用户名/昵称搜索用户（排除自己和已有好友）
    @Query("SELECT NEW com.guanyu.haigui.pojo.vo.FriendSearchResultVO(u.userId, u.username, u.avatar,u.enabled) " +
            "FROM UserInfo u " +
            "WHERE (u.username LIKE CONCAT('%', :keyword, '%')) AND u.userId != :currentUserId")
    List<FriendSearchResultVO> searchPotentialFriends(
            @Param("keyword") String keyword,
            @Param("currentUserId") Long currentUserId
    );

    // 检查是否已经是好友（双向）
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr WHERE (fr.user.userId = :currentUserId AND fr.friend.userId = :targetUserId AND fr.status = :status) OR (fr.user.userId = :targetUserId AND fr.friend.userId = :currentUserId AND fr.status = :status)")
    boolean isAlreadyFriend(@Param("currentUserId") Long currentUserId, @Param("targetUserId") Long targetUserId, @Param("status") FriendStatus status);


    /**
     * 查询当前用户的所有好友（含未读消息数、最后一条消息）
     * @param currentUserId 当前用户ID
     * @return 好友信息DTO列表
     */
    @Query(nativeQuery = true, value = """
        WITH FriendIds AS (
            SELECT DISTINCT
                IF(fr.user_id = :currentUserId, fr.friend_id, fr.user_id) AS friendId
            FROM friend_relations fr
            WHERE fr.status = 'ACCEPTED'
              AND (fr.user_id = :currentUserId OR fr.friend_id = :currentUserId)
        )
        SELECT
            fi.friendId AS friendId,
            u.nickname AS nickname,
            u.avatar AS avatar,
            (SELECT COUNT(*)
             FROM chat_private_messages m
             WHERE m.receiver_id = :currentUserId
               AND m.sender_id = fi.friendId
               AND m.is_read = 0) AS unreadCount,
            (SELECT m.content
             FROM chat_private_messages m
             WHERE (m.sender_id = :currentUserId AND m.receiver_id = fi.friendId)
                OR (m.sender_id = fi.friendId AND m.receiver_id = :currentUserId)
             ORDER BY m.create_time DESC
             LIMIT 1) AS lastMessageContent,
            (SELECT m.create_time
             FROM chat_private_messages m
             WHERE (m.sender_id = :currentUserId AND m.receiver_id = fi.friendId)
                OR (m.sender_id = fi.friendId AND m.receiver_id = :currentUserId)
             ORDER BY m.create_time DESC
             LIMIT 1) AS lastMessageTime
        FROM FriendIds fi
        INNER JOIN sys_user u ON fi.friendId = u.user_id
        GROUP BY fi.friendId, u.nickname, u.avatar
        """)
    List<FriendSearchListVO> findFriendInfoWithMessages(@Param("currentUserId") Long currentUserId);
    /**
     * 根据用户名查找用户（唯一，用于登录）
     */
    Optional<UserInfo> findByUsername(String username);

    /**
     * 根据手机号查找用户（唯一）
     */
    Optional<UserInfo> findByPhone(String phone);

    /**
     * 根据邮箱查找用户（唯一）
     */
    Optional<UserInfo> findByEmail(String email);

    /**
     * 根据用户名模糊查询（用于搜索用户）
     */
    List<UserInfo> findByUsernameContaining(String username);

    /**
     * 根据用户状态查找（如启用/禁用）
     */
    List<UserInfo> findByEnabled(boolean enabled);


}