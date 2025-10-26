package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatRoom;
import com.guanyu.haigui.pojo.model.ChatRoomMember;
import com.guanyu.haigui.pojo.model.ChatRoomMemberId;
import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天室成员仓库（Spring Data JPA 自动生成实现）
 */
@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

    /**
     * 根据聊天室和成员查找成员关系（判断用户是否在房间内）
     */
    Optional<ChatRoomMember> findByChatRoomAndMember(ChatRoom chatRoom, UserInfo member);

    /**
     * 根据用户查找其加入的所有聊天室成员关系
     */
    List<ChatRoomMember> findByMember(UserInfo member);

    /**
     * 根据聊天室查找其所有成员关系
     */
    List<ChatRoomMember> findByChatRoom(ChatRoom chatRoom);

    /**
     * 判断用户是否在某个聊天室内（高效查询）
     */
    boolean existsByChatRoomAndMember(ChatRoom chatRoom, UserInfo member);

    /**
     * 统计某个聊天室的当前成员数
     */
    long countByChatRoom(ChatRoom chatRoom);

    /**
     * 根据【房间ID**和**用户ID**查找成员关系（适配前端参数）
     */
    @Query("SELECT cm FROM ChatRoomMember cm " +
           "WHERE cm.chatRoom.roomId = :roomId " +
           "AND cm.member.userId = :userId")
    Optional<ChatRoomMember> findByRoomIdAndUserId(
            @Param("roomId") String roomId, 
            @Param("userId") Long userId
    );
}