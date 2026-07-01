package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.UserInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatGame c WHERE c.roomId = :roomId")
    Optional<ChatGame> findByRoomIdForUpdate(@Param("roomId") String roomId);

    Optional<ChatGame> findFirstByGameSessionId(String gameSessionId);

    List<ChatGame> findByMembers_Member_UserId(Long userId);

    // 新增方法：查询用户参与的、状态在指定集合中的房间
    @Query("SELECT cg FROM ChatGame cg JOIN cg.members m " +
            "WHERE m.member.userId = :userId AND cg.status IN :statuses")
    List<ChatGame> findByMembers_Member_UserIdAndStatusIn(
            @Param("userId") Long userId,
            @Param("statuses") Collection<RoomStatus> statuses
    );

    List<ChatGame> findByHaiGuiSoup_SoupIdAndStatus(String soupId, RoomStatus status);
}