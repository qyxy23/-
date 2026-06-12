package com.guanyu.haigui.service;

import lombok.Builder;
import lombok.Value;

public interface ChatMessageRetentionService {

    @Value
    @Builder
    class RetentionStats {
        int privateDeletedByTime;
        int privateDeletedByCount;
        int groupDeletedByTime;
        int groupDeletedByCount;

        public int totalDeleted() {
            return privateDeletedByTime + privateDeletedByCount + groupDeletedByTime + groupDeletedByCount;
        }
    }

    /** 执行私聊 + 群聊 retention（时间窗 + 条数窗） */
    RetentionStats runRetention();

    /** 发送后轻量裁剪：仅当前私聊对 */
    void trimPrivateConversation(Long userId1, Long userId2);

    /** 发送后轻量裁剪：仅当前群 */
    void trimGroupConversation(String groupId);

}
