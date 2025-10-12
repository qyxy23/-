package com.guanyu.haigui.pojo.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI聊天会话实体类（对应ai_chat_sessions表）
 * 说明：字段名与数据库列名一致（或通过MyBatis结果映射转换）
 */
@Builder
@Data
public class AiChatSession {
    /** 会话唯一ID（UUID，对应数据库session_id） */
    private Long sessionId;

    /** 关联用户ID（对应数据库user_id） */
    private Long userId;

    /** 会话标题（默认"新对话"，对应数据库title） */
    private String title;

    /** 会话创建时间（对应数据库create_time） */
    private LocalDateTime createTime;

    /** 最后一条消息时间（对应数据库update_time） */
    private LocalDateTime updateTime;

    /** 逻辑删除标记（0=未删除，1=已删除，对应数据库is_deleted） */
    private Integer isDeleted;
}