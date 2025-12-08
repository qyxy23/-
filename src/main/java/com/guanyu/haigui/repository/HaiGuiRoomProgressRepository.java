package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiRoomProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface HaiGuiRoomProgressRepository extends JpaRepository<HaiGuiRoomProgress, Long> {
    
    @Query("SELECT r.triggeredFragmentIds FROM HaiGuiRoomProgress r WHERE r.roomId = :roomId")
    List<Long> findTriggeredFragmentIds(@Param("roomId") String roomId);
    
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM HaiGuiRoomProgress r " +
           "WHERE r.roomId = :roomId AND r.taskId = :taskId AND r.completed = true")
    boolean isTaskCompleted(@Param("roomId") String roomId, @Param("taskId") Long taskId);
    
    @Modifying
    @Query("UPDATE HaiGuiRoomProgress r " +
           "SET r.completed = :completed, " +
           "    r.triggeredFragmentIds = :fragments, " +
           "    r.completionTime = :time " +
           "WHERE r.roomId = :roomId AND r.taskId = :taskId")
    void updateTaskStatus(
        @Param("roomId") String roomId,
        @Param("taskId") Long taskId,
        @Param("completed") boolean completed,
        @Param("fragments") List<Long> fragments,
        @Param("time") LocalDateTime time
    );
    
    @Modifying
    @Query("UPDATE HaiGuiRoomProgress r " +
           "SET r.triggeredFragmentIds = :fragments " +
           "WHERE r.roomId = :roomId")
    void addTriggeredFragments(
        @Param("roomId") String roomId,
        @Param("fragments") List<Long> fragments
    );
}
