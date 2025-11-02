package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatGameRepository extends JpaRepository<ChatGame, String> {
    List<ChatGame> findByCreator(UserInfo creator);
    List<ChatGame> findByStatus(RoomStatus status);
    List<ChatGame> findByStatusAndCurrentMembersLessThan(RoomStatus status, int requiredMembersThreshold);
    List<ChatGame> findByRoomNameContaining(String roomName);
    List<ChatGame> findByCreatorAndStatus(UserInfo creator, RoomStatus status);

    @Query("SELECT r FROM ChatGame r WHERE r.roomId = :roomId")
    Optional<ChatGame> findByRoomId(@Param("roomId") String roomId);

    List<ChatGame> findByMembers_Member_UserId(Long userId);
}