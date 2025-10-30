package com.guanyu.haigui.utils;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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