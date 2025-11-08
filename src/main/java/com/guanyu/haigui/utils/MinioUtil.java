package com.guanyu.haigui.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.guanyu.haigui.context.BaseContext;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 工具类（封装核心操作，修复静态注入和分片上传问题）
 */
@Slf4j
@Component
public class MinioUtil {

    // 静态字段（类级）
    private static String ENDPOINT;
    private static String ACCESS_KEY;
    private static String SECRET_KEY;
    private static MinioClient MINIO_CLIENT;

    // Setter 方法注入静态字段（Spring 会调用）
    @Value("${minio.endpoint}")
    public void setEndpoint(String endpoint) {
        ENDPOINT = endpoint;
    }

    @Value("${minio.access-key}")
    public void setAccessKey(String accessKey) {
        ACCESS_KEY = accessKey;
    }

    @Value("${minio.secret-key}")
    public void setSecretKey(String secretKey) {
        SECRET_KEY = secretKey;
    }

    @Value("${minio.bucket.avatars}") // 注入头像桶名（user-avatars）
    private String avatarsBucket;


    @Value("${minio.endpoint}") // 注入MinIO地址
    private String minioEndpoint;

    /**
     * 确保存储桶存在（不存在则创建）
     */
    public static void ensureBucketExists(String bucketName) {
        try {
            if (!MINIO_CLIENT.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                MINIO_CLIENT.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("头像桶创建成功 → {}", bucketName);
            }
        } catch (Exception e) {
            log.error("创建头像桶失败 → {}", bucketName, e);
            throw new RuntimeException("系统错误：无法初始化存储桶", e);
        }
    }


    public String generateAvatarUrl(MultipartFile avatarFile) {
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
        if (!org.springframework.util.StringUtils.startsWithIgnoreCase(contentType, "image/")) {
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

        return avatarUrl;
     }

    /**
     * 删除旧头像（可选，避免存储冗余）
     */
    public void deleteAvatar(String oldAvatarUrl) {
        try {
            // 从URL解析MinIO对象名（需匹配你的URL格式，如http://endpoint/bucket/avatars/123/xxx.jpg）
            String oldObjectName = parseObjectNameFromUrl(oldAvatarUrl, minioEndpoint, avatarsBucket);
            if (org.springframework.util.StringUtils.hasText(oldObjectName)) {
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

    /**
     * 初始化 MinIO 客户端（Spring Bean 初始化完成后执行）
     */
    @PostConstruct
    public void initMinioClient() {
        try {
            // 校验配置非空
            if (StringUtils.isAnyBlank(ENDPOINT, ACCESS_KEY, SECRET_KEY)) {
                throw new RuntimeException("MinIO 配置错误：请检查 endpoint/access-key/secret-key！");
            }

            // 确保 ENDPOINT 格式正确（包含协议）
            String normalizedEndpoint = ENDPOINT.startsWith("http") ? ENDPOINT : "http://" + ENDPOINT;

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            MINIO_CLIENT = MinioClient.builder()
                    .endpoint(normalizedEndpoint)
                    .credentials(ACCESS_KEY, SECRET_KEY)
                    .httpClient(okHttpClient)
                    .build();
            log.info("MinIO 客户端初始化成功 → Endpoint: {}", normalizedEndpoint);
        } catch (Exception e) {
            log.error("MinIO 客户端初始化失败！", e);
            throw new RuntimeException("MinIO 客户端初始化失败，请检查配置！", e);
        }
    }


    // ==================== 1. 桶管理 ====================
    public static boolean createBucket(String bucketName) {
        try {
            boolean exists = MINIO_CLIENT.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                MINIO_CLIENT.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("存储桶创建成功 → {}", bucketName);
            }
            return true;
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            log.error("创建存储桶失败 → 桶名: {}", bucketName, e);
            return false;
        }
    }

    // ==================== 2. 文件上传 ====================
    /**
     * 上传本地文件（兼容全版本 SDK）
     */
    public static boolean uploadFile(String bucketName, String objectName, String localFilePath) {
        try (InputStream inputStream = Files.newInputStream(Paths.get(localFilePath))) {
            MINIO_CLIENT.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, new File(localFilePath).length(), -1)
                            .build()
            );
            log.info("文件上传成功 → 桶: {}, 对象: {}, 本地路径: {}", bucketName, objectName, localFilePath);
            return true;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("文件上传失败 → 桶: {}, 对象: {}, 本地路径: {}", bucketName, objectName, localFilePath, e);
            return false;
        }
    }


    /**
     * 大文件上传（使用 putObject 自动分片，推荐方式）
     */
    public static boolean uploadLargeFile(String bucketName, String objectName, String localFilePath) {
        try {
            File file = new File(localFilePath);
            if (!file.exists()) {
                log.error("本地文件不存在 → {}", localFilePath);
                return false;
            }

            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                // 使用 MinIO 自动分片上传（默认启用 multipart）
                MINIO_CLIENT.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.length(), -1)
                                .build()
                );
            }

            log.info("大文件上传成功 → 对象: {}, 本地路径: {}", objectName, localFilePath);
            return true;
        } catch (Exception e) {
            log.error("大文件上传失败 → 对象: {}, 本地路径: {}", objectName, localFilePath, e);
            return false;
        }
    }


    // ==================== 3. 文件下载 ====================
    public static boolean downloadFile(String bucketName, String objectName, String localSavePath) {
        try (InputStream is = MINIO_CLIENT.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {

            Path savePath = Paths.get(localSavePath);
            Files.createDirectories(savePath.getParent()); // 创建父目录
            Files.copy(is, savePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件下载成功 → 桶: {}, 对象: {}, 保存路径: {}", bucketName, objectName, localSavePath);
            return true;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("文件下载失败 → 桶: {}, 对象: {}, 保存路径: {}", bucketName, objectName, localSavePath, e);
            return false;
        }
    }

    // ==================== 8. 删除文件 ====================
    /**
     * 删除指定桶中的文件（对象）
     * @param bucketName 存储桶名称
     * @param objectName 文件对象名称（含路径，如"user/avatar/123.jpg"）
     * @return 是否删除成功（true=成功，false=失败）
     */
    public static boolean deleteObject(String bucketName, String objectName) {
        // 1. 参数校验（避免空指针或无效请求）
        if (StringUtils.isAnyBlank(bucketName, objectName)) {
            log.error("删除文件失败 → 桶名或对象名为空");
            return false;
        }

        try {
            // 2. 调用MinIO客户端删除对象
            MINIO_CLIENT.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            // 3. 记录成功日志
            log.info("文件删除成功 → 桶: {}, 对象: {}", bucketName, objectName);
            return true;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            // 4. 捕获并处理异常（如对象不存在、权限不足等）
            log.error("文件删除失败 → 桶: {}, 对象: {}", bucketName, objectName, e);
            return true;
        }
    }



    // ==================== 5. 对象列举 ====================
    public static List<String> listObjects(String bucketName) {
        List<String> objectNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = MINIO_CLIENT.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            for (Result<Item> result : results) {
                objectNames.add(result.get().objectName());
            }
            log.info("列举对象成功 → 桶: {}, 共 {} 个对象", bucketName, objectNames.size());
        } catch (MinioException e) {
            log.error("列举对象失败 → 桶: {}", bucketName, e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return objectNames;
    }

    // ==================== 6. 预签名 URL ====================
    public static String generatePresignedUrl(String bucketName, String objectName, int expiry, Method method) {
        try {
            return MINIO_CLIENT.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(method)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiry)
                            .build()
            );
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            log.error("生成预签名 URL 失败 → 桶: {}, 对象: {}, 方法: {}", bucketName, objectName, method, e);
            return null;
        }
    }

    // ==================== 7. 获取客户端（可选） ====================
    public static MinioClient getMinioClient() {
        return MINIO_CLIENT;
    }
}