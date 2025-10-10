package com.guanyu.haigui.pojo.model;

import lombok.Data;

/**
 * @author Guanyu
 *         角色：管理员、用户
 */
@Data
public class Sys_Role {
    // 主键
    private Long id;
    // 角色名称
    private String roleName;
    // 描述
    private String description;
}
