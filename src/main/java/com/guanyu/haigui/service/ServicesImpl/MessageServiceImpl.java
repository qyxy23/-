package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Exception.FriendsException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.dto.PrivateMsgDTO;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.pojo.vo.FriendBasicInfoVO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.repository.FriendRelationRepository;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.MessageService;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.guanyu.haigui.utils.SessionMapUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class MessageServiceImpl implements MessageService {
    private final PrivateMessageRepository messageRepository;
    private final UserInfoRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate; // WebSocket模板
    private final RedisServiceUtil redisServiceUtil;
    private final FriendRelationRepository friendRelationRepository;
    private final SessionMapUtil sessionMapUtil;

    /**
     * 获取所有会话列表（私聊+群聊，支持置顶排序）
     * @return 会话VO列表（按置顶→最新时间排序）
     */
    public List<ChatSessionVO> getChatSessions() {
        long currentUserId = BaseContext.getCurrentId();

        // 1. 处理私聊会话
        List<ChatSessionVO> privateSessions = processPrivateChats(currentUserId);
        List<ChatSessionVO> sessions = new ArrayList<>(privateSessions);

        // 2. 处理群聊会话
        List<ChatSessionVO> groupSessions = processGroupChats(currentUserId);
        sessions.addAll(groupSessions);

        // 3. 排序：置顶优先 → 最新消息时间倒序
        sessions.sort((s1, s2) -> {
            int stickyCompare = Boolean.compare(s2.getIsSticky(), s1.getIsSticky()); // 置顶降序
            if (stickyCompare != 0) return stickyCompare;
            return s2.getLastMessageTime().compareTo(s1.getLastMessageTime()); // 时间降序
        });

        return sessions;
    }

    /**
     * 处理私聊会话
     */
    private List<ChatSessionVO> processPrivateChats(Long currentUserId) {
        // 查询当前用户的好友列表（私聊对象）
        List<FriendBasicInfoVO> basicInfos = userRepository.findFriendBasicInfos(currentUserId);
        if (CollectionUtils.isEmpty(basicInfos)) return Collections.emptyList();

        List<Long> friendIds = basicInfos.stream().map(FriendBasicInfoVO::getUserId).toList();
        Map<Long, Long> unreadMap = new HashMap<>(); // 好友ID→未读数
        Map<Long, PrivateMsgDTO> lastMsgMap = new HashMap<>(); // 好友ID→最后一条消息
        Map<Long, Boolean> stickyMap = new HashMap<>(); // 好友ID→是否置顶

        // 批量查Redis（未读数、最后消息、置顶状态）
        fetchPrivateChatDataFromRedis(currentUserId, friendIds, unreadMap, lastMsgMap, stickyMap);

        // 数据库补充缺失数据
        fetchPrivateChatDataFromDB(currentUserId, friendIds, unreadMap, lastMsgMap, stickyMap);

        // 组装私聊VO
        return basicInfos.stream().map(basic -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setSessionId(String.valueOf(basic.getUserId())); // 私聊用对方用户ID标识
            vo.setChatType("PRIVATE");
            vo.setChatName(basic.getUsername());
            vo.setChatAvatar(basic.getAvatar());
            vo.setUnreadCount(unreadMap.getOrDefault(basic.getUserId(), 0L));

            // 最后一条消息
            PrivateMsgDTO lastMsg = lastMsgMap.get(basic.getUserId());
            if (lastMsg != null) {
                vo.setLastMessageContent(lastMsg.getContent());
                vo.setLastMessageTime(lastMsg.getTime());
                vo.setLastSenderId(basic.getUserId()); // 最后发送者是对方
            }

            vo.setIsSticky(stickyMap.getOrDefault(basic.getUserId(), false));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 从Redis获取私聊数据（未读数、最后消息、置顶状态）
     */
    private void fetchPrivateChatDataFromRedis(Long currentUserId, List<Long> friendIds,
                                               Map<Long, Long> unreadMap,
                                               Map<Long, PrivateMsgDTO> lastMsgMap,
                                               Map<Long, Boolean> stickyMap) {
        for (Long friendId : friendIds) {
            // 未读数
            Long unread = redisServiceUtil.selectUnreadMsgCountFromRedis(currentUserId, friendId);
            if (unread != null) unreadMap.put(friendId, unread);

            // 最后一条消息
            PrivateMsgDTO lastMsg = redisServiceUtil.selectLastMessageFromRedis(currentUserId, friendId);
            if (lastMsg != null) lastMsgMap.put(friendId, lastMsg);

            // 是否置顶
            List<Object[]> stickyRes = userRepository.isPrivateSticky(currentUserId, friendId);
            if (!stickyRes.isEmpty()) {
                stickyMap.put(friendId, (Boolean) stickyRes.get(0)[0]);
            }
        }
    }

    /**
     * 从数据库补充私聊数据（未读数、最后消息、置顶状态）
     */
    private void fetchPrivateChatDataFromDB(Long currentUserId, List<Long> friendIds,
                                            Map<Long, Long> unreadMap,
                                            Map<Long, PrivateMsgDTO> lastMsgMap,
                                            Map<Long, Boolean> stickyMap) {
        // 补充未读数
        List<Long> missingUnreadIds = friendIds.stream()
                .filter(id -> !unreadMap.containsKey(id))
                .collect(Collectors.toList());
        if (!missingUnreadIds.isEmpty()) {
            List<Object[]> unreadResults = userRepository.countUnreadMessagesByFriendIds(currentUserId, missingUnreadIds);
            unreadResults.forEach(arr -> {
                Long friendId = (Long) arr[0];
                Long count = (Long) arr[1];
                unreadMap.put(friendId, count);
                // 补充置顶状态
                List<Object[]> stickyRes = userRepository.isPrivateSticky(currentUserId, friendId);
                if (!stickyRes.isEmpty()) {
                    stickyMap.put(friendId, (Boolean) stickyRes.get(0)[0]);
                }
            });
            // 回写Redis
            Map<Long, Long> unreadFromDb = unreadResults.stream()
                    .collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));
            unreadFromDb.forEach((friendId, count) ->
                    redisServiceUtil.updateUnreadMsgCount(currentUserId, friendId, count)
            );
        }

        // 补充最后一条消息
        List<Long> missingLastMsgIds = friendIds.stream()
                .filter(id -> !lastMsgMap.containsKey(id))
                .collect(Collectors.toList());
        if (!missingLastMsgIds.isEmpty()) {
            List<Object[]> lastMsgResults = userRepository.findLastMessageByFriendIds(currentUserId, missingLastMsgIds);
            lastMsgResults.forEach(arr -> {
                Long friendId = (Long) arr[0];
                String content = (String) arr[1];
                LocalDateTime time = ((Timestamp) arr[2]).toLocalDateTime();
                lastMsgMap.put(friendId, new PrivateMsgDTO(content, time));
                // 补充置顶状态
                List<Object[]> stickyRes = userRepository.isPrivateSticky(currentUserId, friendId);
                if (!stickyRes.isEmpty()) {
                    stickyMap.put(friendId, (Boolean) stickyRes.get(0)[0]);
                }
            });
            // 回写Redis
            Map<Long, PrivateMsgDTO> lastMsgFromDb = lastMsgResults.stream()
                    .collect(Collectors.toMap(arr -> (Long) arr[0], arr ->
                            new PrivateMsgDTO((String) arr[1], ((Timestamp) arr[2]).toLocalDateTime())
                    ));
            lastMsgFromDb.forEach((friendId, msg) ->
                    redisServiceUtil.updateLastMsg(msg, currentUserId, friendId)
            );
        }
    }

    /**
     * 处理群聊会话
     */
    private List<ChatSessionVO> processGroupChats(Long currentUserId) {
        List<Object[]> groupChats = userRepository.findActiveGroupChatsByUserId(currentUserId);
        if (CollectionUtils.isEmpty(groupChats)) return Collections.emptyList();

        return groupChats.stream().map(group -> {
            String roomId = (String) group[0];
            String roomName = (String) group[1];
            String groupAvatar = (String) group[2]; // 群头像

            ChatSessionVO vo = new ChatSessionVO();
            vo.setSessionId(roomId);
            vo.setChatType("GROUP");
            vo.setChatName(roomName);
            vo.setChatAvatar(groupAvatar); // 设置群头像

            // 1. 是否置顶
            List<Object[]> stickyRes = userRepository.isGroupSticky(currentUserId, roomId);
            vo.setIsSticky(!stickyRes.isEmpty() && (Boolean) stickyRes.get(0)[0]);

            // 2. 最后一条消息
            List<Object[]> lastMsgRes = userRepository.findLastGroupMessage(roomId);
            if (!lastMsgRes.isEmpty()) {
                String content = (String) lastMsgRes.get(0)[0];
                LocalDateTime time = ((Timestamp) lastMsgRes.get(0)[1]).toLocalDateTime();
                vo.setLastMessageContent(content);
                vo.setLastMessageTime(time);
                // 最后发送者ID
                List<Object[]> senderRes = userRepository.findLastGroupMessageSenderId(roomId);
                if (!senderRes.isEmpty()) {
                    vo.setLastSenderId((Long) senderRes.get(0)[0]);
                }
            }

            // 3. 未读消息数
            List<Object[]> unreadRes = userRepository.countGroupUnreadMessages(currentUserId, roomId);
            vo.setUnreadCount(unreadRes.isEmpty() ? 0L : (Long) unreadRes.get(0)[0]);

            return vo;
        }).collect(Collectors.toList());
    }


    /**
     * 获取两个用户之间的历史消息（分页）
     * @param userId 当前用户ID（查看消息的人）
     * @param receiverId 对话方ID（消息发送者）
     * @param page 页码
     * @param size 页大小
     * @return 历史消息分页VO
     */
    public Page<PrivateMessageVO> getHistoryMessages(Long userId, Long receiverId, int page, int size) {
        // 1. 先查询历史消息（不阻塞返回）
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<PrivateMessage> messages = messageRepository.findHistoryMessagesBetweenUsers(userId, receiverId, pageable);

        // 2. 异步执行：标记该对话为已读（修改数据库+清理Redis）
        asyncMarkAsRead(userId, receiverId);

        // 3. 立即返回历史消息结果
        return messages.map(PrivateMessageVO::fromEntity);
    }

    /**
     * 异步标记对话为已读（使用预配置线程池）
     * @param currentUserId 当前用户ID（接收者）
     * @param friendId 好友ID（发送者）
     */
    @Async("taskExecutor") // 指定使用配置的"taskExecutor"线程池
    public void asyncMarkAsRead(Long currentUserId, Long friendId) {
        try {
            // 1. 数据库：标记该好友的未读消息为已读
            userRepository.batchMarkMessagesAsRead(currentUserId, Collections.singleton(friendId));
            // 2. Redis：清理该好友的未读缓存
            redisServiceUtil.clearUnreadMsgCount(currentUserId, friendId);
            log.info("异步标记已读完成：用户{} -> 好友{}", currentUserId, friendId);
        } catch (Exception e) {
            log.error("异步标记已读失败：用户{} -> 好友{}", currentUserId, friendId, e);
        }
    }




    @Override
    public PrivateMessageVO sendMessage(PrivateMessageDTO message,String sessionId) {
        Long senderId = sessionMapUtil.getUserIdBySessionId(sessionId);
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

    // // 标记消息已读后清除未读计数
    // private void markMessageAsRead(String messageId) {
    //     PrivateMessage message = messageRepository.findById(messageId).orElseThrow(() -> new RuntimeException("消息不存在"));
    //     if (message.getStatus().equals(MessageStatus.SENT)) {
    //         redisServiceUtil.deleteUnreadMsgCount(message);
    //         message.setStatus(MessageStatus.READ);
    //         messageRepository.save(message);
    //     }
    // }


}