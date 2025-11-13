package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collection;
@Data
public class otherInfoVO {
    private Long userId;
    @Schema(description = "是否为好友")
    private Boolean isFriend;
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
    @Schema(description = "权限列表")
    private Collection<? extends GrantedAuthority> authorities; // 必须有此属性！

    public static otherInfoVO from(UserInfo userInfo, Collection<? extends GrantedAuthority> authorities,Boolean isFriend) {
        otherInfoVO otherInfoVO = new otherInfoVO();
        otherInfoVO.setUserId(userInfo.getUserId());
        otherInfoVO.setUsername(userInfo.getUsername());
        otherInfoVO.setPhone(userInfo.getPhone());
        otherInfoVO.setEmail(userInfo.getEmail());
        otherInfoVO.setCreateTime(userInfo.getCreateTime());
        otherInfoVO.setAvatar(userInfo.getAvatar());
        otherInfoVO.setEnabled(userInfo.getEnabled());
        otherInfoVO.setAuthorities(authorities);
        otherInfoVO.setIsFriend(isFriend);
        return otherInfoVO;
    }
}
