package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.RequestStatus;
import com.guanyu.haigui.pojo.model.GroupJoinRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    /**
     * 根据用户ID、群ID和状态查询申请（避免重复提交）
     */
    Optional<GroupJoinRequest> findByUserUserIdAndGroupGroupIdAndStatus(Long userId, String groupId, RequestStatus status);

    /**
     * 根据申请ID查询申请
     */
    @NotNull Optional<GroupJoinRequest> findById(@NotNull Long requestId);

    /**
     * 关键：查询当前用户管理的群的所有入群申请（分页+按时间倒序）
     * 逻辑：先查用户管理的群ID列表，再匹配这些群的申请
     * @param currentUserId 当前登录用户ID
     * @param pageable 分页参数
     * @return 分页的申请记录
     */
    @Query("SELECT r FROM GroupJoinRequest r " +
            "WHERE r.group.groupId IN (SELECT a.id.groupId FROM ChatGroupAdministrator a WHERE a.id.userId = :currentUserId) " +
            "ORDER BY r.applyTime DESC")
    Page<GroupJoinRequest> findByManagedGroupsOrderByApplyTimeDesc(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable);

    /**
     * 删除用户对群的申请
     */
    @Modifying // 删除操作需要加@Modifying注解（部分JPA版本要求）
    @Query("DELETE FROM GroupJoinRequest r WHERE r.user.userId = :userId AND r.group.groupId = :groupId")
    void deleteByUserUserIdAndGroupGroupId(@Param("userId") Long userId, @Param("groupId") String groupId);
}