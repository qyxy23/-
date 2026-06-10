package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.converter.LongSetConverter;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.Set;

/** 游戏会话任务进度（映射 hai_gui_room_progress 表） */
@Data
@Entity
@Table(name = "hai_gui_room_progress",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"game_session_id", "task_id"}, name = "uk_session_task")
        },
        indexes = {
                @Index(columnList = "game_session_id", name = "idx_game_session_id")
        })
@SQLDelete(sql = "UPDATE hai_gui_room_progress SET is_deleted = 1 WHERE progress_id = ?")
@Where(clause = "is_deleted = 0")
public class HaiGuiGameProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long progressId;

    @Column(name = "game_session_id", nullable = false, length = 36)
    private String gameSessionId;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "triggered_fragment_ids", nullable = false, columnDefinition = "JSON")
    @Convert(converter = LongSetConverter.class)
    private Set<Long> triggeredFragmentIds;

    @Column(columnDefinition = "DATETIME(6)")
    private LocalDateTime completionTime;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;
}
