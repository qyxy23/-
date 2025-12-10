package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.dto.VoteCheckMessage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoteMQProducer {

    private final RocketMQTemplate rocketMQTemplate;
    
    // 主题名称
    private static final String TOPIC = "VOTE_TOPIC";
    
    /**
     * 发送投票检查消息（立即处理）
     */
    public void sendImmediateCheck(VoteCheckMessage message) {
        rocketMQTemplate.convertAndSend(TOPIC + ":IMMEDIATE", message);
    }
    
    /**
     * 发送投票检查消息（延迟处理）
     * @param delayLevel 延迟级别（1-18，对应不同延迟时间）
     */
    public void sendDelayedCheck(VoteCheckMessage message, int delayLevel) {
        rocketMQTemplate.syncSend(
            TOPIC + ":DELAYED",
                MessageBuilder.withPayload(message).build(),
                rocketMQTemplate.getProducer().getSendMsgTimeout(),
                delayLevel
        );
    }
    
    /**
     * 发送投票超时消息
     */
    public void sendTimeoutMessage(VoteCheckMessage message) {
        rocketMQTemplate.convertAndSend(TOPIC + ":TIMEOUT", message);
    }
}