package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.RegisterType;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.pojo.model.UserInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class RegisterRequest extends UserInfo implements Serializable {
    // 注册方式
    private RegisterType type;

    // 角色
    private UserRoleEnum role;

}