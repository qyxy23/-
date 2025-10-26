package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMessageRepository extends JpaRepository<GroupMessage, String> {

    /**
     * 根据群房间ID分页查询历史消息（按发送时间倒序）
     * @param roomId 群房间ID
     * @param pageable 分页参数
     * @return 分页后的群消息
     */
    Page<GroupMessage> findByRoom_RoomId(String roomId, Pageable pageable);

    /**
     * 根据群房间ID倒序查询消息（最新在前），支持分页限制数量
     * @param roomId 群房间ID
     * @param pageable 分页参数（页码0开始，按createTime倒序）
     * @return 分页后的群消息
     */
    Page<GroupMessage> findByRoom_RoomIdOrderByCreateTimeDesc(String roomId, Pageable pageable);
}