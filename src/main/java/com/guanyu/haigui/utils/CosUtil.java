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

    /**
     * 上传图片并返回公网访问 URL
     */
    public String uploadImage(MultipartFile file) {
        validateImage(file);

        Long userId = BaseContext.getCurrentId();
        String ext = resolveExt(file.getOriginalFilename());
        String objectKey = String.format("avatars/%d/%s.%s", userId, UUID.randomUUID(), ext);

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
            log.info("COS 上传成功 → 用户ID: {}, key: {}", userId, objectKey);
        } catch (Exception e) {
            log.error("COS 上传失败 → 用户ID: {}", userId, e);
            throw new RuntimeException("图片上传失败，请重试", e);
        }

        return buildPublicUrl(objectKey);
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

    private String parseObjectKey(String fileUrl) {
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
}
