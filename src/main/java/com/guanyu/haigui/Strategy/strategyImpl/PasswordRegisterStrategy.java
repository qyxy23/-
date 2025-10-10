package com.guanyu.haigui.Strategy.strategyImpl;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.UserAlreadyExistsException;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.mapper.UserRoleMapper;
import com.guanyu.haigui.pojo.dto.UserRole;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.utils.JwtTokenUtil;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PasswordRegisterStrategy implements RegisterStrategy {
    @Resource
    private JwtTokenUtil jwtUtil;
    @Resource
    private UserDetailsMapper userDetailsMapper;
    @Resource
    private BCryptPasswordEncoder passwordEncoder; // 注入密码编码器
    @Resource
    private UserRoleMapper userRoleMapper;

    @Override
    public CustomUserDetails register(Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        try {
            // 1. 检查用户名是否已经存在
            UserInfo userInfo = userDetailsMapper.selectUserInfoByUsername(username);
            // 如果用户名已经存在，则抛出异常
            if (userInfo != null) {
                throw new UserAlreadyExistsException(username);
            }
            // 2. 编码密码（关键改造：明文 → 哈希值）
            String encodedPassword = passwordEncoder.encode(password); // 编码后的密码
            // 创建用户
            CustomUserDetails CustomUserDetails = new CustomUserDetails();
            CustomUserDetails.setUsername(username);
            CustomUserDetails.setPassword(encodedPassword);
            CustomUserDetails.setRole(UserRoleEnum.USER.name());
            // int insertCount = userDetailsMapper.insert(CustomUserDetails);
            // if (insertCount <= 0) {
            // throw new RuntimeException("用户注册失败，请重试");
            // }

            // 4. 获取数据库生成的用户ID（关键！）
            Long userId = CustomUserDetails.getId();

            // 5. 插入用户角色中间表（关联用户ID和角色ID）
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(UserRoleEnum.USER.getRoleId()); // 角色ID从枚举获取
            int roleInsertCount = userRoleMapper.insertUserRole(userRole);
            if (roleInsertCount <= 0) {
                // 可选：回滚用户插入（若需要事务）
                throw new RuntimeException("用户角色关联失败");
            }
            // 生成【带角色信息】的 JWT Token
            String token = jwtUtil.generateToken(CustomUserDetails);
            CustomUserDetails.setToken(token);
            return CustomUserDetails;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}