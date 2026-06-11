package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cos.audit")
public class CosAuditProperties {

    /** 是否启用上传后 CI 图片审核 */
    private boolean enabled = true;

    /** 头像策略 biz-type（用户头像、群头像） */
    private String avatarBizType = "";

    /** 封面策略 biz-type（上传者海龟汤封面） */
    private String coverBizType = "";
}
