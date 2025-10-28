package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.GroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMessageRepository extends JpaRepository<GroupMessage, String> {

    /**
     * 查询指定房间的历史消息，并加载关联的room和sender（避免延迟加载）
     * @param roomId 房间ID
     * @param pageable 分页参数
     * @return 分页后的GroupMessage（含完整room/sender数据）
     */
    @Query("SELECT gm FROM GroupMessage gm " +
            "JOIN FETCH gm.sender " + // 加载sender关联
            "WHERE gm.room.roomId = :roomId")
    Page<GroupMessage> findByRoomWithAssociations(
            @Param("roomId") String roomId,
            Pageable pageable
    );

    /**
     * 根据群房间ID倒序查询消息（最新在前），支持分页限制数量
     * @param roomId 群房间ID
     * @param pageable 分页参数（页码0开始，按createTime倒序）
     * @return 分页后的群消息
     */
    Page<GroupMessage> findByRoom_RoomIdOrderByCreateTimeDesc(String roomId, Pageable pageable);
}