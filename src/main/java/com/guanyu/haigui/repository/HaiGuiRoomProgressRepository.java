package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiRoomProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface HaiGuiRoomProgressRepository extends JpaRepository<HaiGuiRoomProgress, Long> {

    // 返回 JSON 字符串列表
    @Query("SELECT r.triggeredFragmentIds FROM HaiGuiRoomProgress r WHERE r.roomId = :roomId")
    List<Set<Long>> findTriggeredFragmentIds(@Param("roomId") String roomId);

    // 按任务和房间查询
    @Query("SELECT r.triggeredFragmentIds FROM HaiGuiRoomProgress r WHERE r.roomId = :roomId AND r.taskId = :taskId")
    Set<Long> findFragmentIdsByTask(@Param("roomId") String roomId, @Param("taskId") Long taskId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM HaiGuiRoomProgress r " +
           "WHERE r.roomId = :roomId AND r.taskId = :taskId AND r.completed = true")
    boolean isTaskCompleted(@Param("roomId") String roomId, @Param("taskId") Long taskId);



    // 添加触发的线索（更新所有记录）
    @Modifying
    @Query("UPDATE HaiGuiRoomProgress p SET p.triggeredFragmentIds = :fragments " +
            "WHERE p.roomId = :roomId")
    void addTriggeredFragments(
            @Param("roomId") String roomId,
            @Param("fragments") Set<Long> fragments  // 直接使用 Set<Long>
    );

    List<HaiGuiRoomProgress> findByRoomId(String roomId);

    // 获取所有未完成任务当前的已触发线索
    @Query("SELECT p.taskId, p.triggeredFragmentIds " +
            "FROM HaiGuiRoomProgress p " +
            "WHERE p.roomId = :roomId AND p.taskId IN :taskIds")
    List<Object[]> findTaskFragmentsRaw(
            @Param("roomId") String roomId,
            @Param("taskIds") List<Long> taskIds
    );

    // 更新任务状态和线索
    @Modifying
    @Query("UPDATE HaiGuiRoomProgress p SET " +
            "p.completed = :completed, " +
            "p.triggeredFragmentIds = :fragments, " +
            "p.completionTime = :time " +
            "WHERE p.roomId = :roomId AND p.taskId = :taskId")
    void updateTaskStatus(
            @Param("roomId") String roomId,
            @Param("taskId") Long taskId,
            @Param("completed") boolean completed,
            @Param("fragments") Set<Long> fragments,
            @Param("time") LocalDateTime time
    );

    // 仅更新任务线索（不改变完成状态）
    @Modifying
    @Query("UPDATE HaiGuiRoomProgress p SET " +
            "p.triggeredFragmentIds = :fragments, " +
            "p.updatedAt = :time " +
            "WHERE p.roomId = :roomId AND p.taskId = :taskId")
    void updateTaskFragments(
            @Param("roomId") String roomId,
            @Param("taskId") Long taskId,
            @Param("fragments") Set<Long> fragments,
            @Param("time") LocalDateTime time
    );
}
