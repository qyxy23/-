package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.threeten.bp.LocalDateTime;

@Data
@Entity
@Table(name = "chat_room_members")
public class ChatRoomMember {
    @EmbeddedId // 复合主键
    private ChatRoomMemberId id;

    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("memberId") // 映射复合主键的memberId
    @JoinColumn(name = "member_id", nullable = false)
    private UserInfo member;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId") // 映射复合主键的roomId
    @JoinColumn(name = "room_id",
            nullable = false,
            columnDefinition = "varchar(36)")
    private ChatRoom chatRoom;
}

