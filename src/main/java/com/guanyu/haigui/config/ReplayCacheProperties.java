package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haiqutang.replay-cache")
public class ReplayCacheProperties {

    /** 复盘详情 Redis 缓存天数 */
    private int ttlDays = 3;
}
