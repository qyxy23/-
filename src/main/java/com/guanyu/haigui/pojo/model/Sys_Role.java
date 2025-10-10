package com.guanyu.haigui.pojo.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author Guanyu
 * 角色：管理员、用户
 */
@Data
@TableName("sys_role")
public class Sys_Role {
    @TableId(type = IdType.AUTO)
    // 主键
    private Long id;
    // 角色名称
    private String roleName;
    // 描述
    private String description;
}
