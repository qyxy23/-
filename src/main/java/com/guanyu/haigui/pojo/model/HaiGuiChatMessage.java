package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.QuestionWithAiAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户与AI对话消息实体（对应hai_gui_chat_message表）
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "hai_gui_chat_message")
public class HaiGuiChatMessage {

    /** 消息唯一ID（UUID） */
    @Id
    @Column(name = "message_id", length = 36, nullable = false)
    @Schema(description = "消息唯一ID（UUID）")
    private String messageId;

    /** 关联的房间ID（外键：chat_games.room_id） */
    @Column(name = "room_id", length = 36, nullable = false)
    @Schema(description = "关联的房间ID")
    private String roomId;

    /** 发送人ID（外键：sys_user.user_id） */
    @Column(name = "user_id", nullable = false)
    @Schema(description = "发送人ID")
    private Long userId;

    /** 用户提问内容 */
    @Column(name = "question_content", columnDefinition = "TEXT", nullable = false)
    @Schema(description = "用户提问内容")
    private String questionContent;

    /** AI答复结果（枚举类型） */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_answer", columnDefinition = "ENUM('YES', 'NO', 'PARTIAL', 'UNKNOWN')")
    @Schema(description = "AI答复结果：YES-是，NO-不是，PARTIAL-是或不是，UNKNOWN-不知道")
    private QuestionWithAiAnswer aiAnswer;

    /** 消息发送时间 */
    @CreationTimestamp
    @Column(name = "send_time", updatable = false, nullable = false)
    @Schema(description = "消息发送时间")
    private LocalDateTime sendTime;

    /** 逻辑删除标记（0=未删，1=已删） */
    @Column(name = "is_deleted", nullable = false)
    @Schema(description = "逻辑删除标记（false=未删，true=已删）")
    private Boolean isDeleted = false;


    // 关联对象（可选）
    /*
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", insertable = false, updatable = false)
    private ChatGame chatGame;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserInfo userInfo;
    */
}