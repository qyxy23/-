package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.pojo.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
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
    private List<GrantedAuthority> authorities; // 必须有此属性！
    @Schema(description = "用户角色")
    private UserRoleEnum role;

    public CustomUserDetails(Long userId, String username, List<GrantedAuthority> authorities) {
        super(userId, username);
        this.authorities = authorities;
        this.role = UserRoleEnum.getRoleByRoleId(authorities.get(0).getAuthority());
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
