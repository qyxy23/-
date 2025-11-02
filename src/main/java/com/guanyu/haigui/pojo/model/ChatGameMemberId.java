package com.guanyu.haigui.pojo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 游戏大厅成员复合主键（必须实现Serializable）
 */
@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class ChatGameMemberId implements Serializable {

    /** 成员ID（对应member_id） */
    @Column(name = "member_id")
    private Long memberId;

    /** 房间ID（对应room_id） */
    @Column(name = "room_id", length = 36)
    private String roomId;
}