package com.guanyu.haigui.pojo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 群聊置顶复合主键（必须实现Serializable）
 */
@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class UserGroupStickyId implements Serializable {

    /** 用户ID（对应user_id） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 群聊ID（对应group_id） */
    @Column(name = "group_id", length = 36, nullable = false)
    private String groupId;
}