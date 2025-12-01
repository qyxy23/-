package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ChatGameMemberId;
import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGameMemberRepository extends JpaRepository<ChatGameMember, ChatGameMemberId> {
    Optional<ChatGameMember> findByChatGameAndMember(ChatGame chatGame, UserInfo member);
    List<ChatGameMember> findByMember(UserInfo member);
    List<ChatGameMember> findByChatGame(ChatGame chatGame);
    boolean existsByChatGameAndMember(ChatGame chatGame, UserInfo member);
    long countByChatGame(ChatGame chatGame);
    // 根据房间ID查询所有成员（关联member和chatGame）
    List<ChatGameMember> findByIdRoomId(String roomId);

    @Query("SELECT cm FROM ChatGameMember cm WHERE cm.chatGame.roomId = :roomId AND cm.member.userId = :userId")
    Optional<ChatGameMember> findByRoomIdAndUserId(@Param("roomId") String roomId, @Param("userId") Long userId);



}