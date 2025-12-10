package com.guanyu.haigui.listenrer;

import com.guanyu.haigui.pojo.dto.VoteCheckMessage;
import com.guanyu.haigui.service.ServicesImpl.VoteService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "VOTE_TOPIC", 
    selectorExpression = "IMMEDIATE || DELAYED || TIMEOUT",
    consumerGroup = "vote-consumer-group"
)
public class VoteMQConsumer implements RocketMQListener<VoteCheckMessage> {

    private final VoteService voteService;

    @Override
    public void onMessage(VoteCheckMessage message) {
        switch (message.getMessageType()) {
            case IMMEDIATE:
                voteService.processImmediateVoteCheck(message);
                break;
            case DELAYED:
                voteService.processDelayedVoteCheck(message);
                break;
            case TIMEOUT:
                voteService.processTimeoutVoteCheck(message);
                break;
        }
    }
}