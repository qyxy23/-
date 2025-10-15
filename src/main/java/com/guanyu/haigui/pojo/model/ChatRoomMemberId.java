package com.guanyu.haigui.pojo.model;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 复合主键类（必须实现Serializable）
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class ChatRoomMemberId implements Serializable {
    @Column(name = "member_id")
    private Long memberId;
    @Column(name = "room_id",length = 36)
    private String roomId;
}
