package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.pojo.model.FriendRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository // 确保添加Repository注解，让Spring扫描到
public interface FriendRelationRepository extends JpaRepository<FriendRelation, Long> {

    /**
     * 根据用户ID和好友ID列表查询好友关系
     * @param userId 当前用户ID
     * @param friendIds 待验证的好友ID列表
     * @return 符合条件的好友关系列表
     */
    List<FriendRelation> findByUserUserIdAndFriendUserIdIn(Long userId, List<Long> friendIds);

    /**
     * 查询当前用户【收到的】好友申请（被动方：friend_id = 当前用户ID）
     * @param currentUserId 当前用户ID
     * @param statuses 过滤的状态（如PENDING待处理）
     */
    @Query("SELECT fr FROM FriendRelation fr " +
            "WHERE fr.friend.userId = :currentUserId " +  // 自己是被动方（被添加的人）
            "AND fr.status IN :statuses " +
            "ORDER BY fr.applyTime DESC")           // 按申请时间倒序
    List<FriendRelation> findReceivedApplications(
            @Param("currentUserId") Long currentUserId,
            @Param("statuses") List<FriendStatus> statuses);

    /**
     * 查询当前用户【发送的】好友申请（主动方：user_id = 当前用户ID）
     * @param currentUserId 当前用户ID
     * @param statuses 过滤的状态（如PENDING待处理）
     */
    @Query("SELECT fr FROM FriendRelation fr " +
            "WHERE fr.user.userId = :currentUserId " +   // 自己是主动方（添加别人的人）
            "AND fr.status IN :statuses " +
            "ORDER BY fr.applyTime DESC")
    List<FriendRelation> findSentApplications(
            @Param("currentUserId") Long currentUserId,
            @Param("statuses") List<FriendStatus> statuses);

    /**
     * 检查两个用户之间是否存在指定状态的双向好友关系
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @param status 好友状态（如ACCEPTED）
     * @return 是否存在该关系
     */
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr " +
            "WHERE (fr.user.userId = :userId1 AND fr.friend.userId = :userId2 AND fr.status = :status) " +
            "OR (fr.user.userId = :userId2 AND fr.friend.userId = :userId1 AND fr.status = :status)")
    boolean hasRelationBetweenUsers(@Param("userId1") Long userId1,
                                    @Param("userId2") Long userId2,
                                    @Param("status") FriendStatus status);

    /**
     * 检查当前用户是否已向目标用户发送PENDING申请
     * @param currentUserId 当前用户ID（主动方）
     * @param targetUserId 目标用户ID（被动方）
     * @param status 申请状态（PENDING）
     * @return 是否存在该申请
     */
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr " +
            "WHERE fr.user.userId = :currentUserId " +
            "AND fr.friend.userId = :targetUserId " +
            "AND fr.status = :status")
    boolean hasSentPendingApply(@Param("currentUserId") Long currentUserId,
                                @Param("targetUserId") Long targetUserId,
                                @Param("status") FriendStatus status);

    /**
     * 检查目标用户是否已向当前用户发送PENDING申请
     * @param targetUserId 目标用户ID（主动方）
     * @param currentUserId 当前用户ID（被动方）
     * @param status 申请状态（PENDING）
     * @return 是否存在该申请
     */
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr " +
            "WHERE fr.user.userId = :targetUserId " +
            "AND fr.friend.userId = :currentUserId " +
            "AND fr.status = :status")
    boolean hasReceivedPendingApply(@Param("targetUserId") Long targetUserId,
                                    @Param("currentUserId") Long currentUserId,
                                    @Param("status") FriendStatus status);

    /**
     * 删除两个用户之间的双向好友关系
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     */
    @Modifying
    @Query("DELETE FROM FriendRelation fr " +
            "WHERE (fr.user.userId = :userId1 AND fr.friend.userId = :userId2) " +
            "OR (fr.user.userId = :userId2 AND fr.friend.userId = :userId1)")
    void deleteFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * 查找当前用户与好友的ACCEPTED关系（用于获取备注等信息）
     * @param userId 当前用户ID
     * @param friendId 好友ID
     * @param status 关系状态（ACCEPTED）
     * @return 好友关系实体
     */
    Optional<FriendRelation> findByUserUserIdAndFriendUserIdAndStatus(@Param("userId") Long userId,
                                                                      @Param("friendId") Long friendId,
                                                                      @Param("status") FriendStatus status);
}