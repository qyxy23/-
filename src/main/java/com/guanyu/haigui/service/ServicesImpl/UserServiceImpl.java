package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.UserService;
import com.guanyu.haigui.utils.JwtTokenUtil;
import com.guanyu.haigui.utils.MinioUtil;
import com.guanyu.haigui.utils.RedisServiceUtil;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Resource
    private JwtTokenUtil jwtTokenUtil;
    @Resource
    private RedisServiceUtil redisServiceUtil;
    @Resource
    private UserDetailsMapper UserDetailsMapper;



    @Autowired
    private UserInfoRepository userInfoRepository; // 用户信息DAO（需自己实现）

    @Value("${minio.bucket.avatars}") // 注入头像桶名（user-avatars）
    private String avatarsBucket;

    @Value("${minio.endpoint}") // 注入MinIO地址
    private String minioEndpoint;

    /**
     * 上传用户头像（核心方法）
     * @param avatarFile 前端传来的头像文件（MultipartFile）
     * @return 头像的访问URL（前端可直接使用）
     */
    public String uploadUserAvatar(MultipartFile avatarFile) {
        // -------------------------- 1. 校验文件合法性 --------------------------
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        // 校验大小（不超过10MB）
        long maxSize = 10 * 1024 * 1024;
        if (avatarFile.getSize() > maxSize) {
            throw new IllegalArgumentException("头像大小不能超过10MB");
        }
        // 校验类型（仅允许图片）
        String contentType = avatarFile.getContentType();
        if (!StringUtils.startsWithIgnoreCase(contentType, "image/")) {
            throw new IllegalArgumentException("仅支持图片文件（jpg/png/jpeg等）");
        }

        // -------------------------- 2. 生成唯一对象名（避免冲突） --------------------------
        String originalFilename = avatarFile.getOriginalFilename();
        // 获取文件扩展名
        String ext = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(ext)) {
            throw new IllegalArgumentException("文件格式无效");
        }
        // 对象名格式：avatars/用户ID/UUID.扩展名（按用户分类，避免重名）
        Long userId = BaseContext.getCurrentId();
        String objectName = String.format("avatars/%d/%s.%s", userId, UUID.randomUUID(), ext);

        // -------------------------- 3. 上传文件到MinIO --------------------------
        MinioClient client = MinioUtil.getMinioClient();

        try (InputStream inputStream = avatarFile.getInputStream()) {
            // 调用MinIO客户端上传（直接用InputStream，无需保存本地）
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectName)
                            .stream(inputStream, avatarFile.getSize(), -1) // 流、大小、分片阈值（-1表示自动分片）
                            .build()
            );
            log.info("头像上传MinIO成功 → 用户ID: {}, 对象名: {}", userId, objectName);
        } catch (Exception e) {
            log.error("头像上传MinIO失败 → 用户ID: {}, 文件: {}", userId, originalFilename, e);
            throw new RuntimeException("头像上传失败，请重试", e);
        }

        // -------------------------- 4. 生成头像访问URL --------------------------
        // 方式1：预签名URL（推荐，有效期1小时，安全）
        // String avatarUrl = MinioUtil.generatePresignedUrl(avatarsBucket, objectName, 3600, Method.GET);
        // 方式2：公开访问（需配置桶公开或CORS，适合长期访问）
        String avatarUrl = String.format("%s/%s/%s", minioEndpoint, avatarsBucket, objectName);

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




    /**
     * 删除旧头像（可选，避免存储冗余）
     */
    private void deleteAvatar(String oldAvatarUrl) {
        try {
            // 从URL解析MinIO对象名（需匹配你的URL格式，如http://endpoint/bucket/avatars/123/xxx.jpg）
            String oldObjectName = parseObjectNameFromUrl(oldAvatarUrl, minioEndpoint, avatarsBucket);
            if (StringUtils.hasText(oldObjectName)) {
                boolean success = MinioUtil.deleteObject(avatarsBucket, oldObjectName);
                if (success) {
                    log.info("删除旧头像成功 → 对象名: {}", oldObjectName);
                } else {
                    log.warn("删除旧头像失败 → 对象名: {}", oldObjectName); // 失败时打警告
                }
            }
        } catch (Exception e) {
            log.warn("删除旧头像失败 → URL: {}", oldAvatarUrl, e);
            // 忽略错误，不影响新头像上传
        }
    }



    /**
     * 从URL解析MinIO对象名（根据你的URL格式调整）
     */
    private String parseObjectNameFromUrl(String url, String endpoint, String bucket) throws MalformedURLException {
        URL fullUrl = new URL(url.startsWith("http") ? url : "http://" + url);
        String path = fullUrl.getPath(); // 如 /user-avatars/avatars/123/xxx.jpg
        // 去掉桶名前缀（假设URL路径是 /bucket/对象名）
        return path.replaceFirst("^/" + bucket + "/", ""); // 如 avatars/123/xxx.jpg
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
