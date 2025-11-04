package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.Exception.FriendsException;
import com.guanyu.haigui.Exception.UnauthorizedException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.dto.MsgDTO;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.pojo.vo.FriendBasicInfoVO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.repository.ChatGroupMemberRepository;
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
import org.springframework.util.StringUtils;

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
    private final ChatGroupMemberRepository chatGameMemberRepository;

    /**
     * 置顶/取消置顶单个会话
     * @param sessionId 会话ID
     * @param chatType 会话类型（PRIVATE/GROUP）
     * @param isSticky 是否置顶（null时默认true）
     */
    public void topSingleSession(String sessionId, String chatType, Boolean isSticky) {
        // 1. 参数校验
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(chatType)) {
            throw new IllegalArgumentException("会话ID和类型不能为空");
        }
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null) {
            throw new UnauthorizedException("未登录");
        }
        isSticky = isSticky != null ? isSticky : true; // 默认置顶

        // 2. 根据会话类型处理
        switch (chatType.toUpperCase()) {
            case "PRIVATE" -> topPrivateSession(sessionId, currentUserId, isSticky);
            case "GROUP" -> topGroupSession(sessionId, currentUserId, isSticky);
            default -> throw new IllegalArgumentException("不支持的会话类型：" + chatType);
        }
    }

    /**
     * 置顶/取消置顶私聊会话（核心逻辑）
     * @param sessionId 私聊对方用户ID
     * @param currentUserId 当前用户ID
     * @param isSticky 是否置顶（true=置顶，false=取消）
     */
    public void topPrivateSession(String sessionId, Long currentUserId, boolean isSticky) {
        // 1. 校验权限：必须是好友
        Long otherUserId = parseUserId(sessionId); // sessionId转Long（对方用户ID）
        if (!friendRelationRepository.hasRelationBetweenUsers(currentUserId, otherUserId, FriendStatus.ACCEPTED)) {
            throw new BusinessException("非好友，无法操作该私聊");
        }

        // 2. 根据isSticky执行不同操作
        if (isSticky) {
            // 置顶：执行UPSERT（存在则更新为true，不存在则插入）
            userRepository.insertOrUpdatePrivateSticky(currentUserId, otherUserId, true);
        } else {
            // 取消置顶：删除记录（若存在则删，不存在不影响）
            userRepository.deletePrivateSticky(currentUserId, otherUserId);
        }

        // 3. 同步更新Redis中的置顶状态
        redisServiceUtil.updateUserPrivateSticky(currentUserId, sessionId, isSticky);
    }

    /**
     * 置顶/取消置顶群聊会话
     */
    private void topGroupSession(String sessionId, Long currentUserId, boolean isSticky) {
        // 2.1 校验是群成员
        if (!chatGameMemberRepository.existsByChatGroupGroupIdAndMemberUserId(sessionId, currentUserId)) {
            throw new BusinessException("非群成员，无法置顶该群聊");
        }


        // 2. 根据isSticky执行不同操作
        if (isSticky) {
            // 置顶：执行UPSERT（存在则更新为true，不存在则插入）
            userRepository.insertOrUpdateGroupSticky(currentUserId, sessionId, true);
        } else {
            // 取消置顶：删除记录（若存在则删，不存在不影响）
            userRepository.deleteGroupSticky(currentUserId, sessionId);
        }

        // 2.2 更新Redis中的置顶状态
        redisServiceUtil.updateUserGroupSticky(currentUserId, sessionId, isSticky);
    }

    /**
     * 辅助方法：将sessionId（字符串）转为Long（用户ID）
     */
    private Long parseUserId(String sessionId) {
        try {
            return Long.parseLong(sessionId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("私聊会话ID格式错误（需为对方用户ID）");
        }
    }

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
        sessions.sort(Comparator
                // 第一步：按「是否置顶」降序（置顶在前）
                .comparing(ChatSessionVO::getIsSticky, Comparator.reverseOrder())
                // 第二步：按「最后消息时间」降序，null值放在最后（避免NPE）
                .thenComparing(
                        ChatSessionVO::getLastMessageTime,
                        Comparator.nullsLast(Comparator.reverseOrder()) // 关键：处理null
                )
        );

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
        Map<Long, MsgDTO> lastMsgMap = new HashMap<>(); // 好友ID→最后一条消息
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
            MsgDTO lastMsg = lastMsgMap.get(basic.getUserId());
            if (lastMsg != null) {
                vo.setLastMessageContent(lastMsg.getContent());
                vo.setLastMessageTime(lastMsg.getTime());
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
                                               Map<Long, MsgDTO> lastMsgMap,
                                               Map<Long, Boolean> stickyMap) {
        for (Long friendId : friendIds) {
            // 未读数
            Long unread = redisServiceUtil.selectUnreadMsgCountFromRedis(currentUserId, friendId);
            if (unread != null) unreadMap.put(friendId, unread);

            // 最后一条消息
            MsgDTO lastMsg = redisServiceUtil.selectLastMessageFromRedis(currentUserId, friendId);
            if (lastMsg != null) lastMsgMap.put(friendId, lastMsg);

            // 是否置顶
            Object stickyObj = redisServiceUtil.selectUserPrivateSticky(currentUserId, friendId);
            if (stickyObj != null) {
                // 从Redis获取到值，转换为Boolean
                boolean isSticky = redisServiceUtil.convertToBoolean(stickyObj);
                stickyMap.put(friendId, isSticky);
            }
        }
    }

    /**
     * 从数据库补充私聊数据（未读数、最后消息、置顶状态）
     */
    private void fetchPrivateChatDataFromDB(Long currentUserId, List<Long> friendIds,
                                            Map<Long, Long> unreadMap,
                                            Map<Long, MsgDTO> lastMsgMap,
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
                lastMsgMap.put(friendId, new MsgDTO(content, time));
                // 补充置顶状态
                List<Object[]> stickyRes = userRepository.isPrivateSticky(currentUserId, friendId);
                if (!stickyRes.isEmpty()) {
                    stickyMap.put(friendId, (Boolean) stickyRes.get(0)[0]);
                }
            });
            // 回写Redis
            Map<Long, MsgDTO> lastMsgFromDb = lastMsgResults.stream()
                    .collect(Collectors.toMap(arr -> (Long) arr[0], arr ->
                            new MsgDTO((String) arr[1], ((Timestamp) arr[2]).toLocalDateTime())
                    ));
            lastMsgFromDb.forEach((friendId, msg) ->
                    redisServiceUtil.updateLastMsg(msg, currentUserId, friendId)
            );
        }
    }

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
            vo.setChatAvatar(groupAvatar);

            // 1. 是否置顶（保持原有逻辑，或改为先查Redis）
            List<Object[]> stickyRes = userRepository.isGroupSticky(currentUserId, roomId);
            vo.setIsSticky(!stickyRes.isEmpty() && (Boolean) stickyRes.get(0)[0]);

            // 2. 从Redis查询群聊未读数
            String unreadCount =redisServiceUtil.selectGroupUnreadCount(currentUserId, roomId);

            if (unreadCount != null) {
                Long unreadNum =Long.parseLong(unreadCount);
                vo.setUnreadCount(unreadNum);
            } else {
                // Redis无数据，查数据库
                List<Object[]> unreadRes = userRepository.countGroupUnreadMessages(currentUserId, roomId);
                if (!unreadRes.isEmpty()) {
                    Object unreadValue = unreadRes.get(0)[0];
                    // 正确处理 Long 类型返回值
                    if (unreadValue instanceof Long) {
                        unreadCount = String.valueOf(unreadValue);
                    } else if (unreadValue instanceof Integer) {
                        unreadCount = String.valueOf(unreadValue);
                    } else {
                        unreadCount = unreadValue.toString(); // 兜底处理
                    }
                    vo.setUnreadCount(Long.valueOf(unreadCount));
                    // 回写Redis
                    redisServiceUtil.updateGroupUnreadCount(currentUserId, roomId, unreadCount);
                } else {
                    vo.setUnreadCount(0L); // 默认未读数为0
                }
            }

            // 3. 从Redis查询群聊最后一条消息和发送者
            MsgDTO lastMsg = redisServiceUtil.selectLastGroupMessage(roomId);
            Long lastSenderId = redisServiceUtil.selectLastGroupSenderId(roomId);

            if (lastMsg != null && lastSenderId != null) {
                // Redis有缓存，直接赋值
                vo.setLastMessageContent(lastMsg.getContent());
                vo.setLastMessageTime(lastMsg.getTime());
                UserInfo senderInfo = userRepository.findById(lastSenderId)
                        .orElseThrow(() -> new BusinessException("发送者信息不存在"));
                vo.setLastSenderName(senderInfo.getName());
            } else {
                // Redis无数据，查数据库
                List<Object[]> lastMsgRes = userRepository.findLastGroupMessage(roomId);
                if (!lastMsgRes.isEmpty()) {
                    String content = (String) lastMsgRes.get(0)[0];
                    LocalDateTime time = ((Timestamp) lastMsgRes.get(0)[1]).toLocalDateTime();
                    vo.setLastMessageContent(content);
                    vo.setLastMessageTime(time);

                    // 查最后发送者ID
                    List<Object[]> senderRes = userRepository.findLastGroupMessageSenderId(roomId);
                    if (!senderRes.isEmpty()) {
                        lastSenderId = (Long) senderRes.get(0)[0];

                        // 回写Redis（最后一条消息+发送者ID）
                        redisServiceUtil.updateLastGroupMessage(roomId, new MsgDTO(content, time));
                        redisServiceUtil.updateLastGroupSenderId(roomId, lastSenderId);
                    }
                }
            }
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
        asyncAfterSendMessage(message,senderId);
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
        asyncAfterSendMessage(message,senderId);
        // 2. 推送给接收者
        simpMessagingTemplate.convertAndSendToUser(
                receiver.getUserId().toString(), // 目标用户ID
                "/queue/messages", // 订阅路径
                PrivateMessageVO.fromEntity(privateMessage) // 消息内容
        );
        return PrivateMessageVO.fromEntity(privateMessage);
    }



    // 发送消息后更新缓存
    @Async("taskExecutor") // 指定使用配置的"taskExecutor"线程池
    public void asyncAfterSendMessage(PrivateMessageDTO message,Long userId) {
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