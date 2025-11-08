package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.UserInfoVO;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.UserService;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.MinioUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Resource
    private JwtTokenUtil jwtTokenUtil;
    @Resource
    private RedisServiceUtil redisServiceUtil;
    @Resource
    private UserDetailsMapper UserDetailsMapper;
    @Resource
    private BCryptPasswordEncoder passwordEncoder; // 注入密码编码器

    @Resource
    private MinioUtil minioUtil;
    @Autowired
    private UserInfoRepository userInfoRepository; // 用户信息DAO（需自己实现）



    /**
     * 上传用户头像（核心方法）
     * @param avatarFile 前端传来的头像文件（MultipartFile）
     * @return 头像的访问URL（前端可直接使用）
     */
    public String uploadUserAvatar(MultipartFile avatarFile) {
        String avatarUrl = minioUtil.generateAvatarUrl(avatarFile);
        Long userId = BaseContext.getCurrentId();
        // -------------------------- 5. 更新用户头像到数据库 --------------------------
        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 若用户已有头像，先删除旧文件（避免占用空间）
        if (StringUtils.hasText(userInfo.getAvatar())) {
            deleteAvatar(userInfo.getAvatar());
            log.info("用户头像删除成功 → 用户ID: {}, 旧URL: {}", userId, userInfo.getAvatar());
        }
        userInfo.setAvatar(avatarUrl); // 存储访问URL（而非MinIO内部路径）
        userInfoRepository.save(userInfo);
        log.info("用户头像更新成功 → 用户ID: {}, URL: {}", userId, avatarUrl);

        return avatarUrl;
    }

    @Override
    public String bindPhone(String phone) {
        UserDetailsMapper.updateUserPhone(phone, BaseContext.getCurrentId());
        return "设置成功";
    }

    @Override
    public String bindEmail(String email) {
        UserDetailsMapper.updateUserEmail(email, BaseContext.getCurrentId());
        return "设置邮箱成功";
    }

    @Override
    public String bindPassword(String password) {
        password = passwordEncoder.encode(password);
        UserDetailsMapper.updateUserPassword(password, BaseContext.getCurrentId());
        return "成功设置密码";
    }

    @Override
    public UserInfoVO getUserInfo() {
        return userInfoRepository.findById(BaseContext.getCurrentId())
                .map(UserInfoVO::from)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }


    /**
     * 删除旧头像（可选，避免存储冗余）
     */
    private void deleteAvatar(String oldAvatarUrl) {
        minioUtil.deleteAvatar(oldAvatarUrl);
    }



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
                redisServiceUtil.deleteOnlineStatus(userInfo.getUserId());
            }
            return "退出成功";
        } catch (Exception e) {
            return "退出失败：" + e.getMessage();
        }
    }

}
