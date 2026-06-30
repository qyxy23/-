package com.guanyu.haigui.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.guanyu.haigui.config.CosClientProperties;
import com.guanyu.haigui.context.BaseContext;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;

/**
 * 腾讯云 COS 对象存储工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CosUtil {

    private final COSClient cosClient;
    private final CosClientProperties cosProps;

    /** 用户头像：avatars/user/{userId}/ */
    public UploadedImage uploadUserAvatar(MultipartFile file) {
        Long userId = BaseContext.getCurrentId();
        String ext = resolveExt(file.getOriginalFilename(), file.getContentType());
        String objectKey = String.format("avatars/user/%d/%s.%s", userId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 从字节流上传用户头像（微信临时 URL 下载后入库） */
    public UploadedImage uploadUserAvatarBytes(Long userId, byte[] bytes, String contentType) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        if (bytes.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过10MB");
        }
        String ext = contentType != null && contentType.contains("png") ? "png" : "jpg";
        String objectKey = String.format("avatars/user/%d/%s.%s", userId, UUID.randomUUID(), ext);
        try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType != null ? contentType : "image/jpeg");
            PutObjectRequest request = new PutObjectRequest(
                    cosProps.getBucket(),
                    objectKey,
                    inputStream,
                    metadata
            );
            cosClient.putObject(request);
            log.info("COS 上传成功（微信头像）→ key: {}", objectKey);
            return new UploadedImage(objectKey, buildPublicUrl(objectKey), bytes.length);
        } catch (Exception e) {
            log.error("COS 上传失败 → key: {}", objectKey, e);
            throw new RuntimeException("图片上传失败，请重试", e);
        }
    }

    /** 群头像：avatars/group/{groupId}/ */
    public UploadedImage uploadGroupAvatar(MultipartFile file, String groupId) {
        String ext = resolveExt(file.getOriginalFilename(), file.getContentType());
        String objectKey = String.format("avatars/group/%s/%s.%s", groupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 审核员封面：covers/official/{soupId}/ */
    public UploadedImage uploadSoupCoverOfficial(MultipartFile file, String soupId) {
        String ext = resolveExt(file.getOriginalFilename(), file.getContentType());
        String objectKey = String.format("covers/official/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 上传者封面：covers/pending/{soupId}/ */
    public UploadedImage uploadSoupCoverPending(MultipartFile file, String soupId) {
        String ext = resolveExt(file.getOriginalFilename(), file.getContentType());
        String objectKey = String.format("covers/pending/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** AI 生成封面字节流（审核员 official 路径） */
    public UploadedImage uploadSoupCoverOfficialBytes(String soupId, byte[] bytes, String contentType) {
        if (!StringUtils.hasText(soupId)) {
            throw new IllegalArgumentException("soupId 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        String ext = contentType != null && contentType.contains("png") ? "png" : "jpg";
        String objectKey = String.format("covers/official/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImageBytes(bytes, contentType, objectKey);
    }

    /** AI 文生图草稿：covers/ai-draft/{soupId}/ */
    public UploadedImage uploadSoupCoverAiDraftBytes(String soupId, byte[] bytes, String contentType) {
        if (!StringUtils.hasText(soupId)) {
            throw new IllegalArgumentException("soupId 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        String ext = contentType != null && contentType.contains("png") ? "png" : "jpg";
        String objectKey = String.format("covers/ai-draft/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImageBytes(bytes, contentType, objectKey);
    }

    private UploadedImage uploadImageBytes(byte[] bytes, String contentType, String objectKey) {
        if (bytes.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过10MB");
        }
        try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType != null ? contentType : "image/jpeg");
            PutObjectRequest request = new PutObjectRequest(
                    cosProps.getBucket(),
                    objectKey,
                    inputStream,
                    metadata
            );
            cosClient.putObject(request);
            log.info("COS 上传成功 → key: {}", objectKey);
            return new UploadedImage(objectKey, buildPublicUrl(objectKey), bytes.length);
        } catch (Exception e) {
            log.error("COS 上传失败 → key: {}", objectKey, e);
            throw new RuntimeException("图片上传失败，请重试", e);
        }
    }

    /**
     * 上传图片并返回 objectKey 与公网 URL
     */
    public UploadedImage uploadImage(MultipartFile file, String objectKey) {
        validateImage(file);

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            PutObjectRequest request = new PutObjectRequest(
                    cosProps.getBucket(),
                    objectKey,
                    inputStream,
                    metadata
            );
            cosClient.putObject(request);
            log.info("COS 上传成功 → key: {}", objectKey);
        } catch (Exception e) {
            log.error("COS 上传失败 → key: {}", objectKey, e);
            throw new RuntimeException("图片上传失败，请重试", e);
        }

        return new UploadedImage(objectKey, buildPublicUrl(objectKey), file.getSize());
    }

    /**
     * 根据 URL 删除 COS 对象（失败时不抛异常）
     */
    public void deleteByUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        String objectKey = parseObjectKey(fileUrl);
        if (!StringUtils.hasText(objectKey)) {
            log.warn("无法从 URL 解析 COS 对象 key → {}", fileUrl);
            return;
        }
        try {
            cosClient.deleteObject(cosProps.getBucket(), objectKey);
            log.info("COS 删除成功 → key: {}", objectKey);
        } catch (Exception e) {
            log.warn("COS 删除失败 → key: {}", objectKey, e);
        }
    }

    public String parseObjectKey(String fileUrl) {
        try {
            String path = new URL(fileUrl).getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } catch (Exception e) {
            log.error("URL 解析失败: {}", fileUrl, e);
            return null;
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片大小不能超过10MB");
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return;
        }
        // 微信小程序 chooseAvatar + uploadFile 常不带 image/*，而是 application/octet-stream
        if (contentType == null || "application/octet-stream".equals(contentType)) {
            if (looksLikeImage(file)) {
                return;
            }
        }
        throw new IllegalArgumentException("仅支持图片文件（jpg/png/jpeg等）");
    }

    private boolean looksLikeImage(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            if (header.length >= 3
                    && header[0] == (byte) 0xFF
                    && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF) {
                return true;
            }
            if (header.length >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G') {
                return true;
            }
            if (header.length >= 4
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F') {
                return true;
            }
        } catch (Exception e) {
            log.warn("读取图片头失败", e);
        }
        return false;
    }

    private String resolveExt(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            String ext = StrUtil.subAfter(originalFilename, ".", true);
            if (StrUtil.isNotBlank(ext) && !ext.equals(originalFilename) && ext.length() <= 5) {
                return ext.toLowerCase();
            }
        }
        if (contentType != null) {
            if (contentType.contains("png")) {
                return "png";
            }
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                return "jpg";
            }
            if (contentType.contains("webp")) {
                return "webp";
            }
            if (contentType.contains("gif")) {
                return "gif";
            }
        }
        // chooseAvatar 经 uploadFile 上传时 originalFilename 常为 "avatar" 无后缀
        return "jpg";
    }

    private String buildPublicUrl(String objectKey) {
        String host = cosProps.getHost();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host + "/" + objectKey;
    }

    public record UploadedImage(String objectKey, String url, long sizeBytes) {
    }
}
