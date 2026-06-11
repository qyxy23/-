package com.guanyu.haigui.utils;

import com.guanyu.haigui.Enum.ImageAuditVerdict;
import com.guanyu.haigui.config.CosAuditProperties;
import com.guanyu.haigui.config.CosClientProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 腾讯云数据万象图片同步审核（上传后 API 调用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CiImageAuditService {

    private static final long LARGE_IMAGE_THRESHOLD = 5L * 1024 * 1024;

    private final COSClient cosClient;
    private final CosClientProperties cosProps;
    private final CosAuditProperties auditProps;

    public ImageAuditVerdict auditObjectKey(String objectKey, String bizType, long fileSizeBytes) {
        if (!auditProps.isEnabled()) {
            return ImageAuditVerdict.PASS;
        }
        if (!StringUtils.hasText(bizType)) {
            log.warn("未配置 biz-type，跳过图片审核 objectKey={}", objectKey);
            return ImageAuditVerdict.PASS;
        }

        ImageAuditingRequest request = new ImageAuditingRequest();
        request.setBucketName(cosProps.getBucket());
        request.setObjectKey(objectKey);
        request.setBizType(bizType);
        if (fileSizeBytes > LARGE_IMAGE_THRESHOLD) {
            request.setLargeImageDetect("1");
        }

        try {
            ImageAuditingResponse response = cosClient.imageAuditing(request);
            ImageAuditVerdict verdict = ImageAuditVerdict.fromCiResult(response.getResult());
            log.info("CI 图片审核完成 key={}, bizType={}, result={}, label={}",
                    objectKey, bizType, response.getResult(), response.getLabel());
            return verdict;
        } catch (Exception e) {
            log.error("CI 图片审核失败 key={}, bizType={}", objectKey, bizType, e);
            throw new RuntimeException("图片安全审核失败，请稍后重试", e);
        }
    }

    public ImageAuditVerdict auditAvatar(String objectKey, long fileSizeBytes) {
        return auditObjectKey(objectKey, auditProps.getAvatarBizType(), fileSizeBytes);
    }

    public ImageAuditVerdict auditCover(String objectKey, long fileSizeBytes) {
        return auditObjectKey(objectKey, auditProps.getCoverBizType(), fileSizeBytes);
    }
}
