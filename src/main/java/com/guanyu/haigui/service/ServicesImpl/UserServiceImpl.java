package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.ImageAuditVerdict;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.UserInfoVO;
import com.guanyu.haigui.pojo.vo.otherInfoVO;
import com.guanyu.haigui.repository.FriendRelationRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.UserService;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.CiImageAuditService;
import com.guanyu.haigui.utils.CosUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.Collection;
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
    private CosUtil cosUtil;
    @Resource
    private CiImageAuditService ciImageAuditService;
    @Resource
    private UserInfoRepository userInfoRepository; // 用户信息DAO（需自己实现）
    @Resource
    private FriendRelationRepository friendRelationRepository;



    /**
     * 上传用户头像（核心方法）
     *
     * @param avatarFile 前端传来的头像文件（MultipartFile）
     * @return 头像的访问URL（前端可直接使用）
     */
    public String uploadUserAvatar(MultipartFile avatarFile) {
        Long userId = BaseContext.getCurrentId();
        CosUtil.UploadedImage uploaded = cosUtil.uploadUserAvatar(avatarFile);
        ImageAuditVerdict verdict = ciImageAuditService.auditAvatar(uploaded.objectKey(), uploaded.sizeBytes());
        if (verdict != ImageAuditVerdict.PASS) {
            cosUtil.deleteByUrl(uploaded.url());
            throw new BusinessException(400, verdict == ImageAuditVerdict.REJECT
                    ? "头像含有违规内容，请更换图片"
                    : "头像未通过安全审核，请更换图片");
        }

        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (StringUtils.hasText(userInfo.getAvatar())) {
            deleteAvatar(userInfo.getAvatar());
            log.info("用户头像删除成功 → 用户ID: {}, 旧URL: {}", userId, userInfo.getAvatar());
        }
        userInfo.setAvatar(uploaded.url());
        userInfoRepository.save(userInfo);
        log.info("用户头像更新成功 → 用户ID: {}, URL: {}", userId, uploaded.url());

        return uploaded.url();
    }

    @Override
    public String bindPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(400, "手机号不能为空");
        }
        String trimmed = phone.trim();
        if (!trimmed.matches("^1\\d{10}$")) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        UserDetailsMapper.updateUserPhone(trimmed, BaseContext.getCurrentId());
        return "手机号绑定成功";
    }

    @Override
    public String bindEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        String trimmed = email.trim();
        if (!trimmed.matches("^[\\w.-]+@[\\w.-]+\\.\\w+$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        UserDetailsMapper.updateUserEmail(trimmed, BaseContext.getCurrentId());
        return "邮箱绑定成功";
    }

    @Override
    public String changePassword(String oldPassword, String newPassword) {
        if (!StringUtils.hasText(oldPassword)) {
            throw new BusinessException(400, "请输入旧密码");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException(400, "请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(400, "新密码至少6位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(400, "新密码不能与旧密码相同");
        }
        Long userId = BaseContext.getCurrentId();
        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!StringUtils.hasText(userInfo.getPassword())) {
            throw new BusinessException(400, "当前账号未设置密码，请联系管理员");
        }
        if (!passwordEncoder.matches(oldPassword, userInfo.getPassword())) {
            throw new BusinessException(400, "旧密码不正确");
        }
        UserDetailsMapper.updateUserPassword(passwordEncoder.encode(newPassword), userId);
        return "密码修改成功";
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        // 1. 从SecurityContextHolder中获取当前请求的SecurityContext
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // 2. 从SecurityContext中获取Authentication对象（即之前注入的）
        Authentication authentication = securityContext.getAuthentication();

        // 3. 从Authentication中提取具体信息
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities(); // 获取角色列表（对应Token中的roles）
        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return UserInfoVO.from(userInfo, authorities);
    }

    public otherInfoVO getOtherInfo(Long userId) {
        // 1. 从SecurityContextHolder中获取当前请求的SecurityContext
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // 2. 从SecurityContext中获取Authentication对象（即之前注入的）
        Authentication authentication = securityContext.getAuthentication();

        // 3. 从Authentication中提取具体信息
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities(); // 获取角色列表（对应Token中的roles）
        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return otherInfoVO.from(userInfo, authorities,friendRelationRepository.hasRelationBetweenUsers(BaseContext.getCurrentId(), userId, FriendStatus.ACCEPTED));
    }


    /**
     * 删除旧头像（可选，避免存储冗余）
     */
    private void deleteAvatar(String oldAvatarUrl) {
        cosUtil.deleteByUrl(oldAvatarUrl);
    }


    @Override
    public String logout(String token) {
        try {
            String jwtToken = token.replace("Bearer ", "").trim();
            // Token 已含 userId，无需再查库（避免空闲连接被 MySQL 断开时报 CommunicationsException）
            Long userId = jwtTokenUtil.getUserIdFromToken(jwtToken);
            if (userId != null) {
                redisServiceUtil.deleteOnlineStatus(userId);
            }
            return "退出成功";
        } catch (Exception e) {
            log.warn("退出登录清理在线状态失败: {}", e.getMessage());
            return "退出成功";
        }
    }


}
