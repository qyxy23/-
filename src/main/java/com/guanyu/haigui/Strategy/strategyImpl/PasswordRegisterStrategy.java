package com.guanyu.haigui.Strategy.strategyImpl;

import com.guanyu.haigui.Exception.UserAlreadyExistsException;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.dto.RegisterRequest;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.model.UserRole;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.pojo.vo.LogVO;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Transactional
@Slf4j
@Component
public class PasswordRegisterStrategy implements RegisterStrategy {
    @Resource
    private JwtTokenUtil jwtUtil;
    @Resource
    private UserDetailsMapper userDetailsMapper;
    @Resource
    private BCryptPasswordEncoder passwordEncoder; // 注入密码编码器

    @Override
    public LogVO register(RegisterRequest params) {
        String username = params.getUsername();
        String password = params.getPassword();

        // 1. 检查用户名是否已经存在
        UserInfo userInfo = userDetailsMapper.selectUserInfoByUsername(username);
        // 如果用户名已经存在，则抛出异常
        if (userInfo != null) {
            throw new UserAlreadyExistsException(username);
        }
        // 2. 编码密码（关键改造：明文 → 哈希值）
        String encodedPassword = passwordEncoder.encode(password); // 编码后的密码
        log.info("密码已编码：{}", encodedPassword);
        // 创建用户
        CustomUserDetails CustomUserDetails = new CustomUserDetails();
        CustomUserDetails.setUsername(username);
        CustomUserDetails.setPassword(encodedPassword);
        int insertCount = userDetailsMapper.insert(CustomUserDetails);
        if (insertCount <= 0) {
            throw new RuntimeException("用户注册失败，请重试");
        }

        // 将UserRoleEnum.USER转换为GrantedAuthority（如"ROLE_USER"）
        //TODO:将此处的角色类型写死，只能注册为普通用户
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + params.getRole())
        );
        log.info("权限已设置：{}", authorities);
        CustomUserDetails.setAuthorities(authorities); // 设置到对象中

        // 4. 获取数据库生成的用户ID（关键！）
        Long userId = CustomUserDetails.getUserId();

        // 5. 插入用户角色中间表（关联用户ID和角色ID）
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(params.getRole().getRoleId()); // 角色ID从枚举获取
        int roleInsertCount = userDetailsMapper.insertUserRole(userRole);
        if (roleInsertCount <= 0) {
            // 可选：回滚用户插入（若需要事务）
            throw new RuntimeException("用户角色关联失败");
        }
        // 生成【带角色信息】的 JWT Token
        String token = jwtUtil.generateToken(CustomUserDetails);
        CustomUserDetails.setToken(token);
        LogVO logVO = new LogVO();
        BeanUtils.copyProperties(CustomUserDetails, logVO);
        return logVO;
    }
}