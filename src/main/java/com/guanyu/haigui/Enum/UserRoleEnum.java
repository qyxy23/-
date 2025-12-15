package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
    // 核心角色定义（ID使用Long类型）
    ADMIN(1L, "ADMIN", "系统管理员"),
    USER(2L, "USER", "普通用户"),
    SOUP_AUDITOR(3L, "SOUP_AUDITOR", "海龟汤审核员");  // 新增审核员角色

    private final Long roleId;
    private final String roleCode;  // 角色编码（原roleName改为更明确的roleCode）
    private final String roleDesc;  // 角色描述

    UserRoleEnum(Long roleId, String roleCode, String roleDesc) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleDesc = roleDesc;
    }

    /**
     * 通过角色编码获取枚举（原方法修复）
     * @param roleCode 角色编码（如"ADMIN"）
     * @return 匹配的枚举对象，未找到返回null
     */
    public static UserRoleEnum getByRoleCode(String roleCode) {
        for (UserRoleEnum role : values()) {
            if (role.roleCode.equals(roleCode)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 通过角色ID获取枚举（新增方法）
     * @param roleId 角色ID（如2L）
     * @return 匹配的枚举对象，未找到返回null
     */
    public static UserRoleEnum getByRoleId(Long roleId) {
        for (UserRoleEnum role : values()) {
            if (role.roleId.equals(roleId)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 通过Spring Security的Authority获取枚举（适配框架）
     * @param authority 权限字符串（格式如"ROLE_ADMIN"）
     * @return 匹配的枚举对象
     */
    public static UserRoleEnum fromAuthority(String authority) {
        if (authority == null || !authority.startsWith("ROLE_")) {
            return null;
        }
        String code = authority.substring(5); // 去掉"ROLE_"前缀
        return getByRoleCode(code);
    }
}