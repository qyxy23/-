package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haiqutang.chat-retention")
public class ChatRetentionProperties {

    /** 是否启用定时清理 */
    private boolean enabled = true;

    /** 每个会话（私聊对 / 群）最多保留条数 */
    private int maxMessages = 500;

    /** 消息最长保留天数（滑动时间窗） */
    private int maxDays = 180;

    /** cron 表达式，默认每天 03:30 */
    private String cron = "0 30 3 * * ?";

}
