package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 游戏大厅房间实体（对应chat_games表）
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_games")
public class ChatGame {

    /** 房间唯一ID（UUID，对应room_id） */
    @Id
    @Column(name = "room_id", length = 36, nullable = false)
    private String roomId;

    /** 游戏房间名称（对应room_name） */
    @Column(name = "room_name", nullable = false)
    private String roomName;

    /** 创建者（关联sys_user表，对应creator_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    @Schema(description = "游戏房间创建者")
    private UserInfo creator;

    /** 所需人数（对应required_members） */
    @Column(name = "required_members", nullable = false)
    private Integer requiredMembers;

    /** 当前人数（对应current_members） */
    @Column(name = "current_members", nullable = false)
    private Integer currentMembers;

    /** 房间状态（对应status，枚举存储字符串） */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status;

    /** 创建时间（数据库自动生成，对应create_time） */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;

    /** 更新时间（数据库自动更新，对应update_time） */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 房间成员列表（关联chat_game_members表，可选） */
    @OneToMany(mappedBy = "chatGame", cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(hidden = true) // OpenAPI文档中隐藏
    @Builder.Default
    private Set<ChatGameMember> members = new HashSet<>();
}