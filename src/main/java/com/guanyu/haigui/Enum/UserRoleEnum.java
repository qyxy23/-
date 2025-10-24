package com.guanyu.haigui.Enum;

import lombok.Getter;


@Getter
public enum UserRoleEnum {
    USER(1L, "USER"), // 角色ID=1，角色名=USER
    ADMIN(2L, "ADMIN");

    private final Long roleId;
    private final String roleName;

    UserRoleEnum(Long roleId, String roleName) {
        this.roleId = roleId;
        this.roleName = roleName;
    }

    public static UserRoleEnum getRoleByRoleId(String authority) {
        for (UserRoleEnum role : UserRoleEnum.values()) {
            if (role.getRoleName().equals(authority)) {
                return role;
            }
        }
        return null;
    }
}
