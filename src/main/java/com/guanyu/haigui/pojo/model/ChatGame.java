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

    /** 海龟汤（关联hai_gui_soup表，对应soup_id） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soup_id", nullable = false)
    @Schema(description = "关联的海龟汤")
    private HaiGuiSoup haiGuiSoup;

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
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('WAITING', 'ACTIVE', 'FINISHED','CANCELLED')")
    private RoomStatus status;

    /** 创建时间（数据库自动生成，对应create_time） */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;

    /** 更新时间（数据库自动更新，对应update_time） */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 游戏开始时间（对应start_time） */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 游戏结束时间（对应end_time） */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 房间隐私类型（对应privacy_type） */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "privacy_type", nullable = false, columnDefinition = "ENUM('PUBLIC', 'PRIVATE')")
    private PrivacyType privacyType = PrivacyType.PUBLIC;

    /** 是否需要邀请加入（对应need_invite） */
    @Column(name = "need_invite", nullable = false)
    @Builder.Default
    private Boolean needInvite = false;

    /** 关联的游戏会话ID（对应session_id） */
    @Column(name = "session_id", length = 36)
    private String sessionId;

    /** 房间成员列表（关联chat_game_members表） */
    @OneToMany(mappedBy = "chatGame", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ChatGameMember> members = new HashSet<>();

    // 枚举定义
    public enum PrivacyType {
        PUBLIC, PRIVATE
    }

    @Override
    public String toString() {
        return "ChatGame{" +
                "roomId='" + roomId + '\'' +
                ", soupId=" + (haiGuiSoup != null ? haiGuiSoup.getSoupId() : "null") +
                ", roomName='" + roomName + '\'' +
                ", creatorId=" + (creator != null ? creator.getUserId() : "null") +
                ", requiredMembers=" + requiredMembers +
                ", currentMembers=" + currentMembers +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", privacyType=" + privacyType +
                ", needInvite=" + needInvite +
                ", sessionId='" + sessionId + '\'' +
                ", memberCount=" + (members != null ? members.size() : 0) +  // 只打印成员数量，不展开集合
                '}';
    }
}