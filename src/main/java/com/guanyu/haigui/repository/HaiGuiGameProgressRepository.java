package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiGameProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface HaiGuiGameProgressRepository extends JpaRepository<HaiGuiGameProgress, Long> {

    @Query("SELECT r.triggeredFragmentIds FROM HaiGuiGameProgress r WHERE r.gameSessionId = :gameSessionId")
    List<Set<Long>> findTriggeredFragmentIds(@Param("gameSessionId") String gameSessionId);

    @Query("SELECT r.triggeredFragmentIds FROM HaiGuiGameProgress r WHERE r.gameSessionId = :gameSessionId AND r.taskId = :taskId")
    Set<Long> findFragmentIdsByTask(@Param("gameSessionId") String gameSessionId, @Param("taskId") Long taskId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
           "FROM HaiGuiGameProgress r " +
           "WHERE r.gameSessionId = :gameSessionId AND r.taskId = :taskId AND r.completed = true")
    boolean isTaskCompleted(@Param("gameSessionId") String gameSessionId, @Param("taskId") Long taskId);

    @Modifying
    @Query("UPDATE HaiGuiGameProgress p SET p.triggeredFragmentIds = :fragments " +
            "WHERE p.gameSessionId = :gameSessionId")
    void addTriggeredFragments(
            @Param("gameSessionId") String gameSessionId,
            @Param("fragments") Set<Long> fragments
    );

    List<HaiGuiGameProgress> findByGameSessionId(String gameSessionId);

    @Query("SELECT p.taskId, p.triggeredFragmentIds " +
            "FROM HaiGuiGameProgress p " +
            "WHERE p.gameSessionId = :gameSessionId AND p.taskId IN :taskIds")
    List<Object[]> findTaskFragmentsRaw(
            @Param("gameSessionId") String gameSessionId,
            @Param("taskIds") List<Long> taskIds
    );

    @Modifying
    @Query("UPDATE HaiGuiGameProgress p SET " +
            "p.completed = :completed, " +
            "p.triggeredFragmentIds = :fragments, " +
            "p.completionTime = :time " +
            "WHERE p.gameSessionId = :gameSessionId AND p.taskId = :taskId")
    void updateTaskStatus(
            @Param("gameSessionId") String gameSessionId,
            @Param("taskId") Long taskId,
            @Param("completed") boolean completed,
            @Param("fragments") Set<Long> fragments,
            @Param("time") LocalDateTime time
    );

    @Modifying
    @Query("UPDATE HaiGuiGameProgress p SET " +
            "p.triggeredFragmentIds = :fragments, " +
            "p.updatedAt = :time " +
            "WHERE p.gameSessionId = :gameSessionId AND p.taskId = :taskId")
    void updateTaskFragments(
            @Param("gameSessionId") String gameSessionId,
            @Param("taskId") Long taskId,
            @Param("fragments") Set<Long> fragments,
            @Param("time") LocalDateTime time
    );
}
