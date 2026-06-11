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
        String ext = resolveExt(file.getOriginalFilename());
        String objectKey = String.format("avatars/user/%d/%s.%s", userId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 群头像：avatars/group/{groupId}/ */
    public UploadedImage uploadGroupAvatar(MultipartFile file, String groupId) {
        String ext = resolveExt(file.getOriginalFilename());
        String objectKey = String.format("avatars/group/%s/%s.%s", groupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 审核员封面：covers/official/{soupId}/ */
    public UploadedImage uploadSoupCoverOfficial(MultipartFile file, String soupId) {
        String ext = resolveExt(file.getOriginalFilename());
        String objectKey = String.format("covers/official/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
    }

    /** 上传者封面：covers/pending/{soupId}/ */
    public UploadedImage uploadSoupCoverPending(MultipartFile file, String soupId) {
        String ext = resolveExt(file.getOriginalFilename());
        String objectKey = String.format("covers/pending/%s/%s.%s", soupId, UUID.randomUUID(), ext);
        return uploadImage(file, objectKey);
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
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件（jpg/png/jpeg等）");
        }
    }

    private String resolveExt(String originalFilename) {
        String ext = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(ext)) {
            throw new IllegalArgumentException("文件格式无效");
        }
        return ext.toLowerCase();
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
