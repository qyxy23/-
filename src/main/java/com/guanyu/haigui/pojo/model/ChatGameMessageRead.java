package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 游戏大厅消息已读记录实体（对应chat_game_message_reads表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_game_message_reads")
public class ChatGameMessageRead {

    /** 复合主键（message_id + member_id） */
    @EmbeddedId
    private ChatGameMessageReadId id;

    /** 已读时间（对应read_time） */
    @Column(name = "read_time", nullable = false)
    private LocalDateTime readTime;

    /** 关联的消息（对应message_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("messageId")
    @JoinColumn(name = "message_id", nullable = false)
    private ChatGameMessage message;

    /** 已读成员（对应member_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("memberId")
    @JoinColumn(name = "member_id", nullable = false)
    private UserInfo member;

    /** 重写equals：基于复合主键判断相等性 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatGameMessageRead that = (ChatGameMessageRead) o;
        return Objects.equals(id, that.id);
    }

    /** 重写hashCode：与equals逻辑一致 */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}