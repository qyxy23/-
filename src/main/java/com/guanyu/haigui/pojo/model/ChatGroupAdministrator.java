package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 群聊管理员表（含群主）实体类
 */
@Data // Lombok自动生成getter/setter/toString等
@Entity
@Table(name = "chat_group_administrators") // 对应数据库表名
public class ChatGroupAdministrator {

    /**
     * 复合主键（群ID + 管理员用户ID）
     */
    @EmbeddedId
    private ChatGroupAdministratorId id;

    /**
     * 关联群聊实体（ChatGroup）
     * &#064;MapsId("groupId")：映射复合主键中的groupId字段，自动从ChatGroup获取
     */
    @ManyToOne(fetch = FetchType.LAZY) // 懒加载，避免不必要的查询
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false) // 对应数据库group_id列
    private ChatGroup chatGroup;

    /**
     * 关联用户实体（SysUser）
     * &#064;MapsId("userId")：映射复合主键中的userId字段，自动从SysUser获取
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false) // 对应数据库user_id列
    private UserInfo sysUser;

    /**
     * 是否是群主（0-普通管理员，1-群主）
     * columnDefinition：指定数据库字段类型和默认值
     */
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isOwner;

    /**
     * 任命时间（自动填充当前时间）
     * &#064;CreationTimestamp：Hibernate注解，插入时自动生成当前时间
     */
    @CreationTimestamp
    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime appointTime;
}