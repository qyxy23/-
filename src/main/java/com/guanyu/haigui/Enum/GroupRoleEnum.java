package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum GroupRoleEnum {
    GROUP_MEMBER(1L, "GROUP_MEMBER"), // 角色ID=1，角色名=GROUP_MEMBER
    GROUP_ADMIN(2L, "GROUP_ADMIN"), // 角色ID=2，角色名=GROUP_ADMIN
    GROUP_OWNER(3L, "GROUP_OWNER");

    private final String desc;

    GroupRoleEnum(long l, String desc) {
        this.desc = desc;
    }
}
