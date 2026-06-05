package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cos.client")
public class CosClientProperties {
    /** 公网访问域名，如 https://bucket.cos.region.myqcloud.com */
    private String host;
    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
}
