package com.guanyu.haigui.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;


@Data
public class LogVO {
    private Long userId;
    // 用户名
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
    @Schema(description = "jwt令牌")
    private String token;
    @Schema(description = "权限列表")
    private List<GrantedAuthority> authorities; // 必须有此属性！
}
