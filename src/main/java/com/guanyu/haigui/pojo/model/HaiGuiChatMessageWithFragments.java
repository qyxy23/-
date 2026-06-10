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
                @Index(name = "idx_game_session_id", columnList = "game_session_id"),
                @Index(name = "idx_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
@Data
public class HaiGuiChatMessageWithFragments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "game_session_id", length = 36, nullable = false)
    private String gameSessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_content", columnDefinition = "TEXT", nullable = false)
    private String questionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_answer", columnDefinition = "ENUM('YES','NO','PARTIAL','UNIMPORTANT','UNKNOWN')")
    private QuestionWithAiAnswer aiAnswer;

    @Column(name = "triggered_fragment_ids", columnDefinition = "JSON")
    @Convert(converter = LongSetConverter.class)
    private Set<Long> triggeredFragmentIds;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    private UserInfo userInfo;

    @Transient
    public int getTriggeredFragmentCount() {
        return triggeredFragmentIds == null ? 0 : triggeredFragmentIds.size();
    }

    @Transient
    public boolean hasTriggeredFragment(Long fragmentId) {
        return triggeredFragmentIds != null && triggeredFragmentIds.contains(fragmentId);
    }
}
