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

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @Column(name = "room_id", length = 36)
    private String roomId;

    // 聊天室名称
    @Column(name = "room_name", nullable = false)
    private String roomName;

    @ManyToOne(fetch = FetchType.LAZY) // 使用 jakarta.persistence.ManyToOne
    @JoinColumn(name = "creator_id", nullable = false)
    @Schema(description = "聊天室创建者")
    private UserInfo creator; // 关联用户表

    @Column(name = "required_members", nullable = false)
    private Integer requiredMembers;

    @Column(name = "current_members", nullable = false)
    private Integer currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    // 成员列表（可选，用于查询）
    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @Schema(hidden = true) // 在OpenAPI文档中隐藏此字段
    private Set<ChatRoomMember> members = new HashSet<>();
}