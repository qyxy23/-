package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hai_gui_room_progress",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"room_id", "task_id"}, name = "uk_room_task")
        },
        indexes = {
                @Index(columnList = "room_id,soup_id", name = "idx_room_soup")
        })
@SQLDelete(sql = "UPDATE hai_gui_room_progress SET is_deleted = 1 WHERE progress_id = ?") // 逻辑删除
@Where(clause = "is_deleted = 0") // 查询时过滤已删除数据
public class HaiGuiRoomProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long progressId; // 自增主键

    @Column(nullable = false, length = 36)
    private String roomId; // 关联 chat_games.room_id

    @Column(nullable = false, length = 36)
    private String soupId; // 关联 hai_gui_soup.soup_id

    @Column(nullable = false)
    private Long taskId; // 关联 hai_gui_soup_inference_task.task_id

    @Column(nullable = false)
    private Boolean completed = false; // 默认未完成

    @Column(nullable = false, columnDefinition = "JSON")
    private String triggeredFragmentIds; // 已触发线索 ID 列表（JSON 格式）

    @Column(nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime completionTime; // 完成时间

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDeleted = false; // 逻辑删除标记（默认未删除）

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt; // 创建时间（自动填充）

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt; // 更新时间（自动维护）
}