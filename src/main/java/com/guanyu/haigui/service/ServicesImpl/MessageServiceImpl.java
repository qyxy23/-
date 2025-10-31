package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Exception.FriendsException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.repository.FriendRelationRepository;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.MessageService;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@AllArgsConstructor
@Service
public class MessageServiceImpl implements MessageService {
    private final PrivateMessageRepository messageRepository;
    private final UserInfoRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate; // WebSocket模板
    private final RedisServiceUtil redisServiceUtil;
    private final FriendRelationRepository friendRelationRepository;



    // 获取两个用户之间的历史消息（分页）
    public Page<PrivateMessageVO> getHistoryMessages(Long userId, Long receiverId, int page, int size) {
        redisServiceUtil.clearUnreadMsgCount(receiverId, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<PrivateMessage> messages = messageRepository.findHistoryMessagesBetweenUsers(userId, receiverId, pageable);
        return messages.map(PrivateMessageVO::fromEntity);
    }

    // 统计未读消息数（接收者为当前用户，发送者为好友）
    // public Long countUnreadMessages(Long receiverId, Long senderId) {
    //     return messageRepository.countByReceiverUserIdAndSenderUserIdAndIsReadFalse(receiverId, senderId);
    // }


    @Override
    public PrivateMessageVO sendMessage(PrivateMessageDTO message) {
        Long senderId = BaseContext.getCurrentId();
        PrivateMessage privateMessage = new PrivateMessage();
        privateMessage.setMessageId(UUID.randomUUID().toString());
        // 获取发送者和接收者实体
        UserInfo sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("发送者不存在"));
        UserInfo receiver = userRepository.findById(message.getReceiverId())
                .orElseThrow(() -> new RuntimeException("接收者不存在"));
        if (!friendRelationRepository.hasRelationBetweenUsers(senderId, receiver.getUserId(), FriendStatus.ACCEPTED)) {
            throw new FriendsException("你和对方还不是好友");
        }
        privateMessage.setSender(sender);
        privateMessage.setReceiver(receiver);
        privateMessage.setContent(message.getContent());
        privateMessage.setMessageType(message.getMessageType());
        privateMessage.setStatus(MessageStatus.SENT);
        privateMessage.setIsRead(false);
        privateMessage.setCreateTime(LocalDateTime.now());
        messageRepository.save(privateMessage);
        afterSendMessage(message,senderId);
        // 2. 推送给接收者
        simpMessagingTemplate.convertAndSendToUser(
                receiver.getUserId().toString(), // 目标用户ID
                "/queue/messages", // 订阅路径
                PrivateMessageVO.fromEntity(privateMessage) // 消息内容
        );
        return PrivateMessageVO.fromEntity(privateMessage);
    }



    // 发送消息后更新缓存
    private void afterSendMessage(PrivateMessageDTO message,Long userId) {
        // 更新最后一条消息缓存
        redisServiceUtil.updateLastMsg(message,userId);
        // 如果是发送给好友的消息，更新好友的未读计数
        if (!userId.equals(message.getReceiverId())) {
            redisServiceUtil.updateUnreadMsgCount(message.getReceiverId(),userId);
        }
    }

    // 标记消息已读后清除未读计数
    private void markMessageAsRead(String messageId) {
        PrivateMessage message = messageRepository.findById(messageId).orElseThrow(() -> new RuntimeException("消息不存在"));
        if (message.getStatus().equals(MessageStatus.SENT)) {
            redisServiceUtil.deleteUnreadMsgCount(message);
            message.setStatus(MessageStatus.READ);
            messageRepository.save(message);
        }
    }


}