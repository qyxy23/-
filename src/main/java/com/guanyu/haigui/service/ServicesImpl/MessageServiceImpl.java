package com.guanyu.haigui.service.ServicesImpl;



import cn.hutool.core.lang.UUID;

import com.guanyu.haigui.Enum.FriendStatus;

import com.guanyu.haigui.Enum.MessageChatType;

import com.guanyu.haigui.Enum.MessageStatus;

import com.guanyu.haigui.Exception.BusinessException;

import com.guanyu.haigui.Exception.FriendsException;

import com.guanyu.haigui.Exception.UnauthorizedException;

import com.guanyu.haigui.context.BaseContext;

import com.guanyu.haigui.pojo.dto.ChatMessagesAfterDTO;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;

import com.guanyu.haigui.pojo.model.PrivateMessage;

import com.guanyu.haigui.pojo.model.UserInfo;

import com.guanyu.haigui.pojo.vo.ChatSessionPageVO;

import com.guanyu.haigui.pojo.vo.ChatSessionVO;

import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.repository.ChatGroupMemberRepository;

import com.guanyu.haigui.repository.FriendRelationRepository;

import com.guanyu.haigui.repository.PrivateMessageRepository;

import com.guanyu.haigui.repository.UserInfoRepository;

import com.guanyu.haigui.service.ChatMessageRetentionService;
import com.guanyu.haigui.service.ChatSendIdempotencyService;
import com.guanyu.haigui.service.MessageService;

import com.guanyu.haigui.service.UserChatSessionService;

import com.guanyu.haigui.utils.RedisServiceUtil;

import com.guanyu.haigui.utils.SessionMapUtil;

import com.guanyu.haigui.websocket.StompUserPushService;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Sort;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

@RequiredArgsConstructor

@Service

@Slf4j

public class MessageServiceImpl implements MessageService {

    private final PrivateMessageRepository messageRepository;

    private final UserInfoRepository userRepository;

    private final StompUserPushService stompUserPushService;

    private final UserChatSessionService userChatSessionService;

    private final FriendRelationRepository friendRelationRepository;

    private final SessionMapUtil sessionMapUtil;

    private final ChatGroupMemberRepository chatGameMemberRepository;

    private final RedisServiceUtil redisServiceUtil;

    private final ChatMessageRetentionService chatMessageRetentionService;

    private final ChatSendIdempotencyService chatSendIdempotencyService;



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hardDeletePrivateChatBetween(Long userId1, Long userId2) {
        messageRepository.deleteAllBetweenUsers(userId1, userId2);
        userChatSessionService.removePrivateChatBetween(userId1, userId2);
        redisServiceUtil.clearPrivateChatBetween(userId1, userId2);
        log.info("已硬删除私聊数据: {} <-> {}", userId1, userId2);
    }



    @Override

    public void topSingleSession(String sessionId, String chatType, Boolean isSticky) {

        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(chatType)) {

            throw new IllegalArgumentException("会话ID和类型不能为空");

        }

        Long currentUserId = BaseContext.getCurrentId();

        if (currentUserId == null) {

            throw new UnauthorizedException("未登录");

        }

        isSticky = isSticky != null ? isSticky : true;



        switch (chatType.toUpperCase()) {

            case "PRIVATE" -> topPrivateSession(sessionId, currentUserId, isSticky);

            case "GROUP" -> topGroupSession(sessionId, currentUserId, isSticky);

            default -> throw new IllegalArgumentException("不支持的会话类型：" + chatType);

        }

    }



    private void topPrivateSession(String sessionId, Long currentUserId, boolean isSticky) {

        Long otherUserId = parseUserId(sessionId);

        if (!friendRelationRepository.hasRelationBetweenUsers(currentUserId, otherUserId, FriendStatus.ACCEPTED)) {

            throw new BusinessException(403, "非好友，无法操作该私聊");

        }

        UserInfo peer = userRepository.findById(otherUserId)

                .orElseThrow(() -> new BusinessException(403, "好友不存在"));

        userChatSessionService.ensurePrivateSession(

                currentUserId, otherUserId, peer.getUsername(), peer.getAvatar());

        userChatSessionService.setSticky(currentUserId, sessionId, "PRIVATE", isSticky);

    }



    private void topGroupSession(String sessionId, Long currentUserId, boolean isSticky) {

        if (!chatGameMemberRepository.existsByChatGroupGroupIdAndMemberUserId(sessionId, currentUserId)) {

            throw new BusinessException(403, "非群成员，无法置顶该群聊");

        }

        userRepository.findActiveGroupChatsByUserId(currentUserId).stream()

                .filter(row -> sessionId.equals(row[0]))

                .findFirst()

                .ifPresent(row -> userChatSessionService.ensureGroupSession(

                        currentUserId, sessionId, (String) row[1], (String) row[2]));

        userChatSessionService.setSticky(currentUserId, sessionId, "GROUP", isSticky);

    }



    private Long parseUserId(String sessionId) {

        try {

            return Long.parseLong(sessionId);

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException("私聊会话ID格式错误（需为对方用户ID）");

        }

    }



    @Override

    public List<ChatSessionVO> getStickySessions() {

        Long currentUserId = BaseContext.getCurrentId();

        return userChatSessionService.listStickySessions(currentUserId);

    }



    @Override

    public ChatSessionPageVO getNonStickySessions(int pageSize, String cursor) {

        Long currentUserId = BaseContext.getCurrentId();

        return userChatSessionService.listNonStickySessions(currentUserId, pageSize, cursor);

    }



    @Override

    public Page<PrivateMessageVO> getHistoryMessages(Long userId, Long receiverId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<PrivateMessage> messages = messageRepository.findHistoryMessagesBetweenUsers(userId, receiverId, pageable);

        return messages.map(PrivateMessageVO::fromEntity);

    }



    @Override
    public void clearPrivateSessionUnread(Long friendId) {
        Long currentUserId = BaseContext.getCurrentId();
        userChatSessionService.clearPrivateUnread(currentUserId, friendId);
        redisServiceUtil.clearUnreadMsgCount(currentUserId, friendId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivateMessageVO> getPrivateMessagesAfter(ChatMessagesAfterDTO dto) {
        Long currentUserId = BaseContext.getCurrentId();
        Long peerId = dto.getReceiverId();
        if (peerId == null) {
            throw new IllegalArgumentException("receiverId 不能为空");
        }
        int size = dto.getSize() != null ? Math.min(Math.max(dto.getSize(), 1), 100) : 50;
        LocalDateTime afterTime = resolveAfterTime(currentUserId, String.valueOf(peerId), "PRIVATE", dto.getAfterTime());
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "createTime"));
        return messageRepository.findMessagesAfterBetweenUsers(currentUserId, peerId, afterTime, pageable)
                .stream()
                .map(PrivateMessageVO::fromEntity)
                .toList();
    }

    @Override
    public void clearPrivateChatHistory(Long friendId) {
        Long currentUserId = BaseContext.getCurrentId();
        userChatSessionService.clearChatHistory(currentUserId, String.valueOf(friendId), "PRIVATE");
        redisServiceUtil.clearUnreadMsgCount(currentUserId, friendId);
    }

    private LocalDateTime resolveAfterTime(Long userId, String sessionId, String chatType, LocalDateTime clientAfter) {
        LocalDateTime base = clientAfter != null ? clientAfter : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime clearAt = userChatSessionService.getSession(userId, sessionId, chatType)
                .map(ChatSessionVO::getHistoryClearAt)
                .orElse(null);
        if (clearAt != null && clearAt.isAfter(base)) {
            return clearAt;
        }
        return base;
    }

    @Override

    public PrivateMessageVO sendMessage(PrivateMessageDTO message) {

        Long senderId = BaseContext.getCurrentId();

        return doSendMessage(message, senderId);

    }



    @Override

    public PrivateMessageVO sendMessage(PrivateMessageDTO message, String sessionId) {

        Long senderId = sessionMapUtil.getUserIdBySessionId(sessionId);

        return doSendMessage(message, senderId);

    }



    private PrivateMessageVO doSendMessage(PrivateMessageDTO message, Long senderId) {

        UserInfo sender = userRepository.findById(senderId)

                .orElseThrow(() -> new RuntimeException("发送者不存在"));

        UserInfo receiver = userRepository.findById(message.getReceiverId())

                .orElseThrow(() -> new RuntimeException("接收者不存在"));

        if (!friendRelationRepository.hasRelationBetweenUsers(senderId, receiver.getUserId(), FriendStatus.ACCEPTED)) {

            throw new FriendsException("你和对方还不是好友");

        }

        String clientMsgId = chatSendIdempotencyService.normalizeClientMsgId(message.getClientMsgId());
        if (clientMsgId != null) {
            var existing = chatSendIdempotencyService.findExistingPrivate(
                    senderId, receiver.getUserId(), clientMsgId);
            if (existing.isPresent()) {
                return toPrivateMessageResponse(existing.get(), false);
            }
        }

        PrivateMessage privateMessage = new PrivateMessage();

        privateMessage.setMessageId(UUID.randomUUID().toString());

        privateMessage.setClientMsgId(clientMsgId);

        privateMessage.setSender(sender);

        privateMessage.setReceiver(receiver);

        privateMessage.setContent(message.getContent());

        privateMessage.setMessageType(message.getMessageType());

        privateMessage.setStatus(MessageStatus.SENT);

        privateMessage.setCreateTime(LocalDateTime.now());

        try {
            messageRepository.save(privateMessage);
        } catch (DataIntegrityViolationException ex) {
            if (clientMsgId == null) {
                throw ex;
            }
            PrivateMessage duplicate = chatSendIdempotencyService
                    .findExistingPrivate(senderId, receiver.getUserId(), clientMsgId)
                    .orElseThrow(() -> ex);
            return toPrivateMessageResponse(duplicate, false);
        }

        chatMessageRetentionService.trimPrivateConversation(senderId, receiver.getUserId());

        userChatSessionService.onPrivateMessageSent(

                senderId,

                receiver.getUserId(),

                message.getContent(),

                privateMessage.getCreateTime(),

                sender.getUsername());

        return toPrivateMessageResponse(privateMessage, true);

    }

    private PrivateMessageVO toPrivateMessageResponse(PrivateMessage privateMessage, boolean pushToReceiver) {

        PrivateMessageVO messageVO = PrivateMessageVO.fromEntity(privateMessage);

        messageVO.setChatType(MessageChatType.PRIVATE_MESSAGE);

        if (pushToReceiver) {
            sendToUserPrivateTopic(messageVO);
        }

        return messageVO;

    }



    public void sendToUserPrivateTopic(PrivateMessageVO message) {

        try {

            if (message.getReceiverId() != null) {

                stompUserPushService.pushPrivateChannel(message.getReceiverId(), message);

            }

        } catch (Exception e) {

            log.error("发送到用户专属主题失败: {}", message, e);

        }

    }

}


