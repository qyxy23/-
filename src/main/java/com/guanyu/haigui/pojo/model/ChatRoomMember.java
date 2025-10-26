package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    // 重写 equals：只要 userId 相同，就是同一个用户
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRoomMember that = (ChatRoomMember) o;
        return Objects.equals(id, that.id);
    }

    // 重写 hashCode：与 equals 一致，基于 userId
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

