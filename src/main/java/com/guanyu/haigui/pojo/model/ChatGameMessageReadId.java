package com.guanyu.haigui.pojo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 游戏大厅消息已读复合主键（必须实现Serializable）
 */
@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class ChatGameMessageReadId implements Serializable {

    /** 消息ID（对应message_id） */
    @Column(name = "message_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String messageId;

    /** 成员ID（对应member_id） */
    @Column(name = "member_id", nullable = false)
    private Long memberId;
}