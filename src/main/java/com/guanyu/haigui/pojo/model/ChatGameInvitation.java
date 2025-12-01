package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.InvitationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游戏房间邀请记录实体
 * 对应表：chat_game_invitations
 */
@Data // Lombok 注解：自动生成 Getter/Setter/toString/equals/hashCode
@Entity
@Table(
    name = "chat_game_invitations",
    indexes = {
        @Index(name = "idx_room_id", columnList = "room_id"),       // 房间ID索引
        @Index(name = "idx_invitee_id", columnList = "invitee_id") // 被邀请者ID索引
    }
)
@EqualsAndHashCode(callSuper = false) // 避免继承 Serializable 后的 equals/hashCode 冲突
public class ChatGameInvitation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L; // 序列化版本号

    /**
     * 邀请唯一ID（UUID）
     */
    @Id
    @GeneratedValue(generator = "uuid2") // 使用 Hibernate UUID 生成器
    @Column(name = "invitation_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String invitationId;

    /**
     * 目标房间（关联 chat_games 表）
     */
    @ManyToOne(fetch = FetchType.LAZY) // 懒加载，避免不必要的关联查询
    @JoinColumn(name = "room_id", nullable = false) // 外键：room_id
    private ChatGame chatGame; // 关联 ChatGame 实体（需提前定义）

    /**
     * 邀请者（关联 sys_user 表）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false) // 外键：inviter_id
    private UserInfo inviter; // 关联 SysUser 实体（需提前定义）

    /**
     * 被邀请者（关联 sys_user 表）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false) // 外键：invitee_id
    private UserInfo invitee; // 关联 SysUser 实体（需提前定义）

    /**
     * 邀请状态（枚举：PENDING/ACCEPTED/REJECTED/EXPIRED）
     */
    @Enumerated(EnumType.STRING) // 存储枚举的字符串值（而非 ordinal）
    @Column(nullable = false)
    private InvitationStatus status;


    /**
     * 邀请创建时间（不可更新）
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    // 可选：自定义构造方法（如无参构造已满足需求，可省略）
    public ChatGameInvitation() {
        this.createTime = LocalDateTime.now(); // 默认填充创建时间
    }
}