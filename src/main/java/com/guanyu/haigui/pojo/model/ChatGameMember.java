package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.MemberStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 游戏大厅成员实体（对应chat_game_members表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_game_members")
public class ChatGameMember {

    /** 复合主键（member_id + room_id） */
    @EmbeddedId
    private ChatGameMemberId id;

    /** 加入时间（对应join_time） */
    @Column(name = "join_time", nullable = false)
    private LocalDateTime joinTime;

    /** 成员用户（关联sys_user表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("memberId") // 映射复合主键的memberId
    @JoinColumn(name = "member_id", nullable = false)
    private UserInfo member;

    /** 所属游戏房间（关联chat_games表） */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId") // 映射复合主键的roomId
    @JoinColumn(name = "room_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private ChatGame chatGame;

    /** 成员状态（新增） */
    @Enumerated(EnumType.STRING) // 存储枚举的字符串值（如"ONLINE"）
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.ONLINE; // 默认在线

    /** 重写equals：基于复合主键判断相等性 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatGameMember that = (ChatGameMember) o;
        return Objects.equals(id, that.id);
    }

    /** 重写hashCode：与equals逻辑一致 */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}