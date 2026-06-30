package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "haigui_theory_draft")
@Data
public class HaiGuiTheoryDraft {

    @Id
    @Column(name = "game_session_id", length = 36, nullable = false)
    private String gameSessionId;

    @Column(name = "draft_text", columnDefinition = "TEXT")
    private String draftText;

    @Column(name = "draft_version", nullable = false)
    private Integer draftVersion = 0;

    @Column(name = "editor_user_id")
    private Long editorUserId;

    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
