package com.guanyu.haigui.pojo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 群消息已读记录复合主键（消息ID + 成员ID）
 */
@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatGroupMessageReadId implements Serializable {

    @Column(name = "message_id", length = 36, nullable = false)
    @Schema(description = "关联的群消息ID")
    private String messageId;

    @Column(name = "member_id", nullable = false)
    @Schema(description = "已读成员ID")
    private Long memberId;
}