package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.pojo.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录返回的数据格式")
public class CustomUserDetails extends UserInfo implements UserDetails, Serializable {

    @Schema(description = "jwt令牌")
    private String token;

    @Schema(description = "权限列表")
    private Collection<? extends GrantedAuthority> authorities;

    @Schema(description = "用户角色")
    private UserRoleEnum role;

    // 推荐使用的构造方法
    public CustomUserDetails(UserInfo userInfo, Collection<? extends GrantedAuthority> authorities) {
        super(userInfo.getUserId(), userInfo.getUsername());
        this.authorities = authorities;
        this.token = null; // 初始无token

        // 从权限中提取角色
        extractRoleFromAuthorities();
    }

    // 兼容旧构造方法（需修复逻辑）
    public CustomUserDetails(Long userId, String username, List<GrantedAuthority> authorities) {
        super(userId, username);
        this.authorities = authorities;

        // 修复角色提取逻辑（使用新版UserRoleEnum）
        extractRoleFromAuthorities();
    }

    // 核心角色提取逻辑
    private void extractRoleFromAuthorities() {
        if (authorities == null || authorities.isEmpty()) {
            this.role = UserRoleEnum.USER; // 默认角色
            return;
        }

        // 尝试从权限中提取角色（支持多种格式）
        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            UserRoleEnum foundRole = parseRoleFromAuthority(auth);
            if (foundRole != null) {
                this.role = foundRole;
                return;
            }
        }

        // 未找到匹配角色时使用默认值
        this.role = UserRoleEnum.USER;
    }

    // 解析权限字符串为角色枚举
    private UserRoleEnum parseRoleFromAuthority(String authority) {
        // 格式1: ROLE_ADMIN (Spring Security默认格式)
        if (authority.startsWith("ROLE_")) {
            return UserRoleEnum.fromAuthority(authority);
        }

        // 格式2: ADMIN (纯角色编码)
        UserRoleEnum byCode = UserRoleEnum.getByRoleCode(authority);
        if (byCode != null) {
            return byCode;
        }

        // 格式3: 角色ID字符串
        try {
            Long roleId = Long.parseLong(authority);
            return UserRoleEnum.getByRoleId(roleId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 添加token的构造方法
    public CustomUserDetails withToken(String token) {
        this.token = token;
        return this;
    }

    @Override
    public String getName() {
        if (getUserId() != null) {
            return String.valueOf(getUserId());
        }
        return super.getName();
    }

    // 实现UserDetails接口方法
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return super.getPassword(); // 从父类获取密码
    }

    @Override
    public String getUsername() {
        return super.getUsername(); // 从父类获取用户名
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // 根据业务需求实现
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // 根据业务需求实现
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 根据业务需求实现
    }

    @Override
    public boolean isEnabled() {
        return true; // 根据业务需求实现
    }

    // 便捷的角色检查方法
    public boolean hasRole(UserRoleEnum role) {
        return this.role == role;
    }

    public boolean isAdmin() {
        return hasRole(UserRoleEnum.ADMIN);
    }

    public boolean isAuditor() {
        return hasRole(UserRoleEnum.SOUP_AUDITOR);
    }
}