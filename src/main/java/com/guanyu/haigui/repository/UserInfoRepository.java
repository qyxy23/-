package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 检查是否已经是好友（双向）
    @Query("SELECT COUNT(fr) > 0 FROM FriendRelation fr WHERE (fr.user.userId = :currentUserId AND fr.friend.userId = :targetUserId AND fr.status = :status) OR (fr.user.userId = :targetUserId AND fr.friend.userId = :currentUserId AND fr.status = :status)")
    boolean isAlreadyFriend(@Param("currentUserId") Long currentUserId, @Param("targetUserId") Long targetUserId, @Param("status") FriendStatus status);


    /**
     * 查询当前用户的所有好友（含未读消息数、最后一条消息）
     * @param currentUserId 当前用户ID
     * @return 好友信息DTO列表
     */
    @Query(nativeQuery = true, name = "UserInfo.findFriendInfoWithMessages")
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