package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.service.UserService;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


@Service
public class UserServiceImpl implements UserService {
    @Resource
    private JwtTokenUtil jwtTokenUtil;
    @Resource
    private RedisServiceUtil redisServiceUtil;
    @Resource
    private UserDetailsMapper UserDetailsMapper;



    @Override
    public String logout(String token) {
        try {
            // 1. 去除 Token 前缀（如 "Bearer "）
            String jwtToken = token.replace("Bearer ", "");
            // 2. 从 Token 中获取用户名
            String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            // 3. 查询用户 ID
            UserInfo userInfo = UserDetailsMapper.selectUserInfoByUsername(username);
            if (userInfo != null) {
                // 4. 删除 Redis 中的在线状态键
                redisServiceUtil.deleteOnlineStatus(userInfo.getId());
            }
            return "退出成功";
        } catch (Exception e) {
            return "退出失败：" + e.getMessage();
        }
    }

}
