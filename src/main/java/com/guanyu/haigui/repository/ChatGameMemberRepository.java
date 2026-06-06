package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.MemberStatus;
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

    @Query("SELECT cm FROM ChatGameMember cm JOIN FETCH cm.member WHERE cm.id.roomId = :roomId")
    List<ChatGameMember> findByRoomIdWithMember(@Param("roomId") String roomId);

    @Query("SELECT cm FROM ChatGameMember cm WHERE cm.chatGame.roomId = :roomId AND cm.member.userId = :userId")
    Optional<ChatGameMember> findByRoomIdAndUserId(@Param("roomId") String roomId, @Param("userId") Long userId);


    List<ChatGameMember> findByIdRoomIdAndStatusInOrderByJoinTimeAsc(String roomId, List<MemberStatus> list);


    @Query(value = "SELECT " +
            "  g.room_id AS roomId, " +
            "  g.room_name AS title, " +
            "  soup.soup_surface AS soupContent, " +
            "  COALESCE(g.end_time, g.create_time) AS endTime, " +
            "  gs.score AS finalScore " +
            "FROM chat_game_members m " +
            "JOIN chat_games g ON m.room_id = g.room_id " +
            "JOIN hai_gui_soup soup ON g.soup_id = soup.soup_id " +
            "LEFT JOIN haigui_game_session gs ON g.session_id = gs.session_id " +
            "WHERE m.member_id = :userId " +
            "  AND g.status = 'FINISHED' " +
            "ORDER BY COALESCE(g.end_time, g.create_time) DESC",
            nativeQuery = true)
    List<Object[]> findUserGameRooms(@Param("userId") Long userId);
}