package com.guanyu.haigui.repository;

import com.guanyu.haigui.Projection.ChatGroupMemberProjection;
import com.guanyu.haigui.Projection.ChatGroupMemberWithRole;
import com.guanyu.haigui.pojo.model.ChatGroupMember;
import com.guanyu.haigui.pojo.model.ChatGroupMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, ChatGroupMemberId> {
    /**
     * 检查用户是否已在群中
     * @param groupId 群ID
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean existsByChatGroupGroupIdAndMemberUserId(@Param("groupId") String groupId,@Param("userId") Long userId);

    /** 批量查询群成员信息 */
    List<ChatGroupMember> findByIdGroupIdAndIdMemberIdIn(String groupId, List<Long> senderIds);

    // 根据群ID获取所有群成员（用于后续筛选在线成员）
    List<ChatGroupMember> findByChatGroupGroupId(String groupId);

    Page<ChatGroupMember> findByChatGroupGroupId(String groupId, Pageable pageable);

    boolean existsById_MemberIdAndId_GroupId(Long newAdminUserId, String groupId);

    @Query("SELECT m AS member, a AS administrator " +
            "FROM ChatGroupMember m " +
            "LEFT JOIN ChatGroupAdministrator a " +
            "ON m.member.userId = a.id.userId AND m.chatGroup.groupId = a.id.groupId " + // 路径正确：a.id是复合主键对象
            "WHERE m.chatGroup.groupId = :groupId")
    Page<ChatGroupMemberWithRole> findMembersWithRole(@Param("groupId") String groupId, Pageable pageable);


    @Query("SELECT m AS member, a AS administrator " +
            "FROM ChatGroupMember m " +
            "LEFT JOIN ChatGroupAdministrator a " +
            "ON m.member.userId = a.id.userId AND m.chatGroup.groupId = a.id.groupId " + // 路径正确：a.id是复合主键对象
            "WHERE m.chatGroup.groupId = :groupId")
    List<ChatGroupMemberWithRole> findAllMembersWithRole(@Param("groupId") String groupId);



    /**
     * 查询用户是否是群成员（返回投影，避免循环引用）
     * @param userId 用户ID
     * @param groupId 群ID
     * @return 投影对象（仅包含必要字段）
     */
    @Query("SELECT " +
            "m.member.userId AS memberId, " +  // 关联UserInfo的userId
            "m.chatGroup.groupId AS groupId, " +
            "m.joinTime AS joinTime " +
            "FROM ChatGroupMember m " +
            "WHERE m.member.userId = :userId AND m.chatGroup.groupId = :groupId")
    Optional<ChatGroupMemberProjection> findByMemberUserIdAndChatGroupGroupId(
            @Param("userId") Long userId,
            @Param("groupId") String groupId
    );
}

