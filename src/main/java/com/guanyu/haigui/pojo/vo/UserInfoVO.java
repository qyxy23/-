package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVO {
    private Long userId;
    // 用户名
    @Schema(description = "用户名")
    private String username;
    // 手机号
    private String phone;
    // 邮箱
    private String email;
    // 创建时间
    private LocalDateTime createTime;
    // 头像
    @Schema(description = "头像")
    private String avatar;
    // 状态
    private boolean enabled;

    public static UserInfoVO from(UserInfo userInfo) {
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserId(userInfo.getUserId());
        userInfoVO.setUsername(userInfo.getUsername());
        userInfoVO.setPhone(userInfo.getPhone());
        userInfoVO.setEmail(userInfo.getEmail());
        userInfoVO.setCreateTime(userInfo.getCreateTime());
        userInfoVO.setAvatar(userInfo.getAvatar());
        userInfoVO.setEnabled(userInfo.getEnabled());
        return userInfoVO;
    }
}
