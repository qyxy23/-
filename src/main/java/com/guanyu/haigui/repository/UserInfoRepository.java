package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.FriendBasicInfoVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户信息仓库（Spring Data JPA 自动生成实现）
 */
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    /**
     * 根据群聊ID获取最后一条消息的发送者ID
     * @param roomId 群聊ID（对应 chat_groups.group_id）
     * @return 发送者ID列表（最多1条，无结果则为空）
     */
    @Query(nativeQuery = true, value = """
        SELECT sender_id
        FROM chat_group_messages
        WHERE group_id = :roomId
        ORDER BY create_time DESC
        LIMIT 1
    """)
    List<Object[]> findLastGroupMessageSenderId(@Param("roomId") String roomId);


    // -------------------------- 群聊相关查询 --------------------------
    /**
     * 查询当前用户参与的所有活跃群聊（成员且群状态正常）
     * 注：chat_groups 表无 status 字段，移除原 status 过滤条件
     * @param currentUserId 当前用户ID
     * @return 群聊ID、名称、头像
     */
    @Query(nativeQuery = true, value = """
    SELECT cg.group_id, cg.group_name, cg.group_avatar
    FROM chat_groups cg
    INNER JOIN chat_group_members cgm ON cg.group_id = cgm.group_id
    WHERE cgm.member_id = :currentUserId  -- 仅当前用户是群成员
""")
    List<Object[]> findActiveGroupChatsByUserId(@Param("currentUserId") Long currentUserId);

    /**
     * 统计当前用户对某群聊的未读消息数
     * @param currentUserId 当前用户ID（消息接收者）
     * @param roomId 群聊ID（对应 chat_groups.group_id）
     * @return 未读消息数
     */
    @Query(nativeQuery = true, value = """
    SELECT COUNT(*) AS unreadCount
    FROM chat_group_messages gm
    LEFT JOIN chat_group_message_reads r
      ON gm.message_id = r.message_id
         AND r.member_id = :currentUserId  -- 仅关联当前用户的已读记录
    WHERE gm.group_id = :roomId  -- 目标群聊
      AND r.message_id IS NULL   -- 未读（无已读记录）
""")
    List<Object[]> countGroupUnreadMessages(
            @Param("currentUserId") Long currentUserId,
            @Param("roomId") String roomId
    );


    /**
     * 获取某群聊的最后一条消息（内容+时间+发送者ID）
     * @param roomId 群聊ID（对应 chat_groups.group_id）
     * @return 内容、时间、发送者ID
     */
    @Query(nativeQuery = true, value = """
    SELECT content, create_time, sender_id
    FROM chat_group_messages
    WHERE group_id = :roomId  -- 目标群聊
    ORDER BY create_time DESC  -- 按发送时间倒序
    LIMIT 1                    -- 取最后一条
""")
    List<Object[]> findLastGroupMessage(@Param("roomId") String roomId);

    /**
     * 群聊置顶：存在则更新，不存在则插入（依赖联合唯一约束：user_id + group_id）
     * @param currentUserId 当前用户ID
     * @param roomId 群ID（sessionId）
     * @param isSticky 是否置顶（true=置顶）
     */
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query(nativeQuery = true, value = """
    INSERT INTO user_group_sticky (user_id, group_id, is_sticky)
    VALUES (:currentUserId, :roomId, :isSticky)
    ON DUPLICATE KEY UPDATE is_sticky = :isSticky
""")
    void insertOrUpdateGroupSticky(
            @Param("currentUserId") Long currentUserId,
            @Param("roomId") String roomId,
            @Param("isSticky") Boolean isSticky
    );


    /**
     * 群聊取消置顶：删除置顶记录
     * @param currentUserId 当前用户ID
     * @param roomId 群ID（sessionId）
     */
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query(nativeQuery = true, value = """
    DELETE FROM user_group_sticky
    WHERE user_id = :currentUserId AND group_id = :roomId
""")
    void deleteGroupSticky(
            @Param("currentUserId") Long currentUserId,
            @Param("roomId") String roomId
    );


    /**
     * 检查当前用户是否置顶某群聊
     * @param currentUserId 当前用户ID
     * @param roomId 群聊ID（对应 chat_groups.group_id）
     * @return 是否置顶（1=是，0=否）
     */
    @Query(nativeQuery = true, value = """
    SELECT is_sticky
    FROM user_group_sticky
    WHERE user_id = :currentUserId
      AND group_id = :roomId  -- 目标群聊
""")
    List<Object[]> isGroupSticky(
            @Param("currentUserId") Long currentUserId,
            @Param("roomId") String roomId
    );
    // -------------------------- 私聊置顶查询 --------------------------
    /**
     * 检查当前用户是否置顶某私聊
     * @param currentUserId 当前用户ID
     * @param friendId 好友ID（对方用户ID）
     * @return 是否置顶（1=是，0=否）
     */
    @Query(nativeQuery = true, value = """
        SELECT is_sticky
        FROM user_private_chat_sticky
        WHERE user_id = :currentUserId AND other_user_id = :friendId
    """)
    List<Object[]> isPrivateSticky(@Param("currentUserId") Long currentUserId, @Param("friendId") Long friendId);


    /**
     * 私聊置顶：存在则更新，不存在则插入（依赖联合唯一约束：user_id + other_user_id）
     * @param currentUserId 当前用户ID
     * @param friendId 对方用户ID（sessionId）
     * @param isSticky 是否置顶（true=置顶）
     */
    @Modifying // 标记为修改操作
    @Transactional(rollbackFor = Exception.class)
    @Query(nativeQuery = true, value = """
        INSERT INTO user_private_chat_sticky (user_id, other_user_id, is_sticky)
        VALUES (:currentUserId, :friendId, :isSticky)
        ON DUPLICATE KEY UPDATE is_sticky = :isSticky
    """)
    void insertOrUpdatePrivateSticky(
            @Param("currentUserId") Long currentUserId,
            @Param("friendId") Long friendId,
            @Param("isSticky") Boolean isSticky
    );

    /**
     * 私聊取消置顶：删除置顶记录
     * @param currentUserId 当前用户ID
     * @param friendId 对方用户ID（sessionId）
     */
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query(nativeQuery = true, value = """
    DELETE FROM user_private_chat_sticky
    WHERE user_id = :currentUserId AND other_user_id = :friendId
""")
    void deletePrivateSticky(
            @Param("currentUserId") Long currentUserId,
            @Param("friendId") Long friendId
    );

    // 根据用户名/昵称搜索用户（排除自己和已有好友）
    /**
     * 搜索潜在好友（分页+过滤）
     * @param keyword 搜索关键字
     * @param currentUserId 当前用户ID
     * @param pageable 分页参数
     * @return 分页后的好友结果
     */
    @Query("SELECT NEW com.guanyu.haigui.pojo.vo.FriendSearchResultVO(u.userId, u.username, u.avatar, u.enabled) " +
            "FROM UserInfo u " +
            "WHERE " +
            "   u.username LIKE CONCAT('%', :keyword, '%') " + // 匹配关键字
            "   AND u.userId != :currentUserId " + // 排除自己
            "   AND NOT EXISTS ( " + // 排除已是好友的情况
            "       SELECT fr FROM FriendRelation fr " +
            "       WHERE ((fr.user.userId = :currentUserId AND fr.friend.userId = u.userId AND fr.status = 'ACCEPTED') " +
            "              OR (fr.user.userId = u.userId AND fr.friend.userId = :currentUserId AND fr.status = 'ACCEPTED'))" +
            "   ) " +
            "   AND NOT EXISTS ( " + // 排除当前用户已发的pending申请
            "       SELECT fr FROM FriendRelation fr " +
            "       WHERE fr.user.userId = :currentUserId AND fr.friend.userId = u.userId AND fr.status = 'PENDING'" +
            "   ) " +
            "   AND NOT EXISTS ( " + // 排除对方已发的pending申请
            "       SELECT fr FROM FriendRelation fr " +
            "       WHERE fr.user.userId = u.userId AND fr.friend.userId = :currentUserId AND fr.status = 'PENDING'" +
            "   )"
    )
    Page<FriendSearchResultVO> searchPotentialFriendsWithFilters(
            @Param("keyword") String keyword,
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );

    /**
     * 批量标记当前用户对指定好友的未读消息为已读（数据库层面）
     * @param currentUserId 当前用户ID（消息接收者）
     * @param friendIds 好友ID列表（消息发送者）
     */
    @Modifying // 标记为更新操作（非查询）
    @Transactional(rollbackFor = Exception.class) // 事务保障，异常则回滚
    @Query(value = """
        UPDATE chat_private_messages m
        SET m.is_read = TRUE  -- 标记为已读（对应Boolean的true）
        WHERE m.receiver_id = :currentUserId  -- 接收者是当前用户
          AND m.sender_id IN (:friendIds)     -- 发送者是指定好友
          AND m.is_read = FALSE               -- 仅更新未读消息
    """, nativeQuery = true) // 原生SQL查询
    void batchMarkMessagesAsRead(
            @Param("currentUserId") Long currentUserId,
            @Param("friendIds") Collection<Long> friendIds
    );


    // 检查是否已经是好友（双向）
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr WHERE (fr.user.userId = :currentUserId AND fr.friend.userId = :targetUserId AND fr.status = :status) OR (fr.user.userId = :targetUserId AND fr.friend.userId = :currentUserId AND fr.status = :status)")
    boolean isAlreadyFriend(@Param("currentUserId") Long currentUserId, @Param("targetUserId") Long targetUserId, @Param("status") FriendStatus status);


    /**
     * 查询当前用户的所有好友（含未读消息数、最后一条消息）
     * @param currentUserId 当前用户ID
     * @return 好友信息DTO列表
     */
    @Query(nativeQuery = true, name = "UserInfo.findFriendBasicInfos")
    List<FriendBasicInfoVO> findFriendBasicInfos(@Param("currentUserId") Long currentUserId);

    /**
     * 统计当前用户对每个好友的未读消息数（仅好友发给当前用户且未读的消息）
     * @param currentUserId 当前用户ID
     * @param friendIds 好友ID列表（批量查询，避免N+1）
     * @return 数组格式：[friendId, unreadCount]
     */
    @Query(nativeQuery = true, value = """
    SELECT
        m.receiver_id AS friendId,  -- 好友ID（接收者是当前用户）
        COUNT(*) AS unreadCount     -- 未读消息数
    FROM chat_private_messages m
    WHERE m.receiver_id = :currentUserId  -- 消息接收者是当前用户
      AND m.sender_id IN (:friendIds)     -- 发送者是好友
      AND m.is_read = 0                   -- 未读
    GROUP BY m.receiver_id                -- 按好友分组统计
""")
    List<Object[]> countUnreadMessagesByFriendIds(
            @Param("currentUserId") Long currentUserId,
            @Param("friendIds") List<Long> friendIds
    );


    /**
     * 获取当前用户与每个好友的最后一条消息（内容+时间）
     * @param currentUserId 当前用户ID
     * @param friendIds 好友ID列表（批量查询）
     * @return 数组格式：[friendId, lastMessageContent, lastMessageTime]
     */
    @Query(nativeQuery = true, value = """
    WITH LastMessages AS (
        SELECT
            -- 将好友对统一为无序（避免重复）：(user1,user2)和(user2,user1)视为同一好友
            CASE
                WHEN m.sender_id = :currentUserId THEN m.receiver_id
                ELSE m.sender_id
            END AS friendId,
            m.content AS lastMessageContent,
            m.create_time AS lastMessageTime,
            -- 按好友对分组，按时间倒序排序，取第一条（最新）
            ROW_NUMBER() OVER (
                PARTITION BY LEAST(m.sender_id, m.receiver_id), GREATEST(m.sender_id, m.receiver_id)
                ORDER BY m.create_time DESC
            ) AS rn
        FROM chat_private_messages m
        WHERE (m.sender_id = :currentUserId AND m.receiver_id IN (:friendIds))  -- 当前用户发好友
           OR (m.sender_id IN (:friendIds) AND m.receiver_id = :currentUserId)  -- 好友发当前用户
    )
    SELECT friendId, lastMessageContent, lastMessageTime
    FROM LastMessages
    WHERE rn = 1  -- 仅取最新的一条
""")
    List<Object[]> findLastMessageByFriendIds(
            @Param("currentUserId") Long currentUserId,
            @Param("friendIds") List<Long> friendIds
    );

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