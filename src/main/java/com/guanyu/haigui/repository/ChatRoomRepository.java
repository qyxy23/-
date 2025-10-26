package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatRoom;
import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天室仓库（Spring Data JPA 自动生成实现）
 */
@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    /**
     * 根据创建者查找其创建的所有聊天室
     */
    List<ChatRoom> findByCreator(UserInfo creator);

    /**
     * 根据聊天室状态查找（如等待集齐/进行中/已结束）
     */
    List<ChatRoom> findByStatus(RoomStatus status);

    /**
     * 查找【等待集齐成员】的聊天室（当前成员数 < 所需成员数）
     */
    List<ChatRoom> findByStatusAndCurrentMembersLessThan(
            RoomStatus status, 
            int requiredMembersThreshold
    );

    /**
     * 根据房间名称模糊查询
     */
    List<ChatRoom> findByRoomNameContaining(String roomName);

    /**
     * 根据创建者和状态查找聊天室
     */
    List<ChatRoom> findByCreatorAndStatus(UserInfo creator, RoomStatus status);

    /**
     * 根据房间ID查询（冗余方法，方便业务调用）
     */
    @Query("SELECT r FROM ChatRoom r WHERE r.roomId = :roomId")
    Optional<ChatRoom> findByRoomId(@Param("roomId") String roomId);
}