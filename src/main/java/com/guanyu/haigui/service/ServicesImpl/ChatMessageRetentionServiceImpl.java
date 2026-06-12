package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.config.ChatRetentionProperties;
import com.guanyu.haigui.repository.ChatGroupMessageRepository;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.service.ChatMessageRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageRetentionServiceImpl implements ChatMessageRetentionService {

    private final ChatRetentionProperties properties;
    private final PrivateMessageRepository privateMessageRepository;
    private final ChatGroupMessageRepository chatGroupMessageRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetentionStats runRetention() {
        if (!properties.isEnabled()) {
            return RetentionStats.builder().build();
        }

        int maxMessages = Math.max(1, properties.getMaxMessages());
        int maxDays = Math.max(1, properties.getMaxDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxDays);

        int privateByTime = privateMessageRepository.deleteOlderThan(cutoff);
        int groupByTime = chatGroupMessageRepository.deleteOlderThan(cutoff);
        int privateByCount = privateMessageRepository.deleteExceedingMaxPerConversation(maxMessages);
        int groupByCount = chatGroupMessageRepository.deleteExceedingMaxPerGroup(maxMessages);

        RetentionStats stats = RetentionStats.builder()
                .privateDeletedByTime(privateByTime)
                .privateDeletedByCount(privateByCount)
                .groupDeletedByTime(groupByTime)
                .groupDeletedByCount(groupByCount)
                .build();

        if (stats.totalDeleted() > 0) {
            log.info(
                    "chat retention finished: private(time={}, count={}), group(time={}, count={}), maxMessages={}, maxDays={}",
                    privateByTime,
                    privateByCount,
                    groupByTime,
                    groupByCount,
                    maxMessages,
                    maxDays
            );
        }

        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void trimPrivateConversation(Long userId1, Long userId2) {
        if (!properties.isEnabled() || userId1 == null || userId2 == null) {
            return;
        }
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);
        int deleted = privateMessageRepository.trimConversationBeyondMax(minId, maxId, properties.getMaxMessages());
        if (deleted > 0) {
            log.debug("trim private conversation {}<->{} deleted {}", minId, maxId, deleted);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void trimGroupConversation(String groupId) {
        if (!properties.isEnabled() || groupId == null || groupId.isBlank()) {
            return;
        }
        int deleted = chatGroupMessageRepository.trimGroupBeyondMax(groupId, properties.getMaxMessages());
        if (deleted > 0) {
            log.debug("trim group {} deleted {}", groupId, deleted);
        }
    }

}
