package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.RequestStatus;
import com.guanyu.haigui.pojo.model.GroupJoinRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

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
}