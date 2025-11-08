package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGroupMember;
import com.guanyu.haigui.pojo.model.ChatGroupMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<ChatGroupMember> findByMemberUserIdAndChatGroupGroupId(@Param("userId") Long userId,@Param("groupId") String groupId);

    // 根据群ID获取所有群成员（用于后续筛选在线成员）
    List<ChatGroupMember> findByChatGroupGroupId(String groupId);

    Page<ChatGroupMember> findByChatGroupGroupId(String groupId, Pageable pageable);
}