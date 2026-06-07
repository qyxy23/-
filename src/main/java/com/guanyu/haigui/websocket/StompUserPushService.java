package com.guanyu.haigui.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StompUserPushService {

    /** 前端订阅 /topic/private-chat.{userId} */
    public static final String PRIVATE_CHAT_TOPIC_PREFIX = "/topic/private-chat.";

    /** 保留常量供历史引用 */
    public static final String PRIVATE_MESSAGE_DEST = "/queue/private-messages";

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void pushPrivateChannel(Long userId, Object payload) {
        if (userId == null) {
            return;
        }
        String topic = PRIVATE_CHAT_TOPIC_PREFIX + userId;
        simpMessagingTemplate.convertAndSend(topic, payload);
        log.info("用户频道推送: userId={}, topic={}, payloadType={}",
                userId, topic, payload == null ? "null" : payload.getClass().getSimpleName());
    }
}
