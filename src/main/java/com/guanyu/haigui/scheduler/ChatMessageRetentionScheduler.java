package com.guanyu.haigui.scheduler;

import com.guanyu.haigui.config.ChatRetentionProperties;
import com.guanyu.haigui.service.ChatMessageRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageRetentionScheduler {

    private final ChatRetentionProperties properties;
    private final ChatMessageRetentionService chatMessageRetentionService;

    @Scheduled(cron = "${haiqutang.chat-retention.cron:0 30 3 * * ?}")
    public void purgeExpiredMessages() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            chatMessageRetentionService.runRetention();
        } catch (Exception e) {
            log.error("chat retention scheduler failed", e);
        }
    }

}
