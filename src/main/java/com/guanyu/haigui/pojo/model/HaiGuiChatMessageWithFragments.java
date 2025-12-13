package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.QuestionWithAiAnswer;
import com.guanyu.haigui.converter.LongSetConverter;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "hai_gui_chat_message_with_fragments",
        indexes = {
                @Index(name = "idx_room_id", columnList = "room_id"),
                @Index(name = "idx_user_time", columnList = "user_id,created_at"),
                @Index(name = "idx_send_time", columnList = "created_at"),
                @Index(name = "idx_trigger_time", columnList = "created_at"),
                @Index(name = "idx_triggered_fragments", columnList = "triggered_fragment_ids")
        })
@Data
public class HaiGuiChatMessageWithFragments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;  // 自增主键（唯一标识）

    @Column(name = "room_id", length = 36, nullable = false)
    private String roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_content", columnDefinition = "TEXT", nullable = false)
    private String questionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_answer", columnDefinition = "ENUM('YES','NO','PARTIAL','UNKNOWN')", nullable = false)
    private QuestionWithAiAnswer aiAnswer;

    @Column(name = "triggered_fragment_ids", columnDefinition = "JSON")
    @Convert(converter = LongSetConverter.class)
    private Set<Long> triggeredFragmentIds; // 存储线索ID列表

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 关联关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", referencedColumnName = "room_id",
            insertable = false, updatable = false)
    private ChatGame chatGame;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id",
            insertable = false, updatable = false)
    private UserInfo userInfo;

    // 获取触发线索数量
    @Transient
    public int getTriggeredFragmentCount() {
        return triggeredFragmentIds == null ? 0 : triggeredFragmentIds.size();
    }

    // 检查是否触发了特定线索
    @Transient
    public boolean hasTriggeredFragment(Long fragmentId) {
        return triggeredFragmentIds != null && triggeredFragmentIds.contains(fragmentId);
    }
}