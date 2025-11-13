package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGroupAdministrator;
import com.guanyu.haigui.pojo.model.ChatGroupAdministratorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupAdminRepository extends JpaRepository<ChatGroupAdministrator, ChatGroupAdministratorId> {
    /**
     * 检查用户是否是群的群主
     * 
     * @param groupId 群ID
     * @param userId  用户ID
     * @return 是群主返回true，否则false
     */
    boolean existsById_GroupIdAndId_UserIdAndIsOwnerTrue(String groupId, Long userId);

    /**
     * 根据群ID和用户ID查询管理员记录
     * 
     * @param groupId 群ID
     * @param userId  用户ID
     * @return 管理员记录（可选）
     */
    Optional<ChatGroupAdministrator> findById_GroupIdAndId_UserId(String groupId, Long userId);

    /**
     * 检查用户是否是群的管理员（含群主）
     * 
     * @param groupId 群ID
     * @param userId  用户ID
     * @return 是管理员返回true，否则false
     */
    boolean existsById_GroupIdAndId_UserId(String groupId, Long userId);

    /**
     * 根据群ID和用户ID查询管理员记录
     * 
     * @param groupId 群ID
     * @param userId  用户ID
     * @return 管理员记录（可选）
     */
    Optional<ChatGroupAdministrator> findByChatGroupGroupIdAndUserUserId(String groupId, Long userId);

    List<ChatGroupAdministrator> findByChatGroupGroupId(String groupId);
}
