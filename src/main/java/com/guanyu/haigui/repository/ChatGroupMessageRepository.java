package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 群聊消息仓储（操作 chat_group_messages 表）
 */
@Repository
public interface ChatGroupMessageRepository extends JpaRepository<GroupMessage, String> {
    /**
     * 查询指定群聊的历史消息，并加载关联的chatGroup和sender（避免延迟加载）
     * @param groupId 群聊ID（对应ChatGroup.groupId）
     * @param pageable 分页参数
     * @return 分页后的GroupMessage（含完整chatGroup/sender数据）
     */
    @Query("SELECT gm FROM GroupMessage gm " +
            "JOIN FETCH gm.chatGroup " + // 加载群聊关联（避免延迟加载）
            "JOIN FETCH gm.sender " +   // 加载发送者关联
            "WHERE gm.chatGroup.groupId = :groupId") // 条件：群聊ID匹配
    Page<GroupMessage> findByChatGroupWithAssociations(
            @Param("groupId") String groupId,
            Pageable pageable
    );

    /**
     * 根据群聊ID分页查询消息（按发送时间降序）
     * @param groupId 群聊ID（对应ChatGroup.groupId）
     * @param pageable 分页参数
     * @return 分页后的消息列表
     */
    Page<GroupMessage> findByChatGroup_GroupIdOrderByCreateTimeDesc(String groupId, Pageable pageable);


    @EntityGraph(value = "GroupMessage.withChatGroup", type = EntityGraph.EntityGraphType.LOAD)
    Page<GroupMessage> findAll(Specification<GroupMessage> spec, Pageable pageable);
}