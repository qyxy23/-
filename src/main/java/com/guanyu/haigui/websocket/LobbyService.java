package com.guanyu.haigui.websocket;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.UUID;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.MessageType;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.Exception.RoomException;
import com.guanyu.haigui.Exception.RoomFullException;
import com.guanyu.haigui.Exception.RoomNotFoundException;
import com.guanyu.haigui.Exception.UserNotInRoomException;
import com.guanyu.haigui.mapper.AiChatSessionMapper;
import com.guanyu.haigui.mapper.ChatRoomMapper;
import com.guanyu.haigui.mapper.ChatRoomMemberMapper;
import com.guanyu.haigui.mapper.GroupMessageMapper;
import com.guanyu.haigui.pojo.dto.CreateRoomRequest;
import com.guanyu.haigui.pojo.dto.LobbyListDTO;
import com.guanyu.haigui.pojo.dto.RoomChatHistoryDTO;
import com.guanyu.haigui.pojo.dto.SendMessageRequest;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import com.guanyu.haigui.pojo.vo.LobbyListVO;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.utils.RedisServiceUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import retrofit2.http.Header;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
@Service
@Slf4j
@AllArgsConstructor
public class LobbyService {
    // 存储大厅ID与成员用户ID的映射（线程安全）
    private final ConcurrentMap<String, Set<ChatRoomMember>> lobbies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CustomUserDetails> sessionUserMap;
    private final RedisServiceUtil redisService;
    private final ChatRoomMapper chatRoomMapper;
    private final ChatRoomMemberMapper chatRoomMemberMapper;
    private final AiChatSessionMapper aiChatSessionMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final GroupMessageMapper groupMessageMapper;
    private final GroupMessageRepository groupMessageRepository;
    private final UserInfoRepository userInfoRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final PrivateMessageRepository privateMessageRepository;


    /**
     * 创建聊天室
     */
    @Transactional // 事务：保证房间创建+成员添加的原子性
    public String createChatRoom(CreateRoomRequest request, String sessionId) {
        String roomName = request.getRoomName();
        Integer requiredMembers = request.getRequiredMembers();
        log.info("用户创建{}人大厅[{}]，会话ID：{}", requiredMembers, roomName, sessionId);

        // 1. 校验基础规则：所需人数≥2
        if (requiredMembers < 2) {
            throw new IllegalArgumentException("所需人数至少2人");
        }

        // 3. 获取**已持久化的创建者实体**
        Long creatorId = getUserDetailsBySessionId(sessionId);
        UserInfo creator = userInfoRepository.findById(creatorId)
                .orElseThrow(() -> {
                    log.error("用户ID[{}]未找到对应用户", creatorId);
                    return new RuntimeException("创建者不存在");
                });

        // 4. 生成唯一房间ID
        String roomId = UUID.randomUUID().toString();

        // 5. 构建并保存ChatRoom实体（关联已存在的创建者）
        ChatRoom room = ChatRoom.builder()
                .roomId(roomId)
                .roomName(roomName)
                .creator(creator) // 关联已持久化的UserInfo
                .requiredMembers(requiredMembers)
                .currentMembers(1) // 初始成员数=创建者自己
                .status(RoomStatus.WAITING)
                .createTime(LocalDateTime.now())
                .build();
        chatRoomRepository.save(room); // JPA自动保存到数据库

        // 6. 构建并保存ChatRoomMember实体（创建者加入房间）
        ChatRoomMember member = ChatRoomMember.builder()
                .id(new ChatRoomMemberId(creatorId, roomId)) // 复合主键：用户ID+房间ID
                .member(creator) // 关联创建者实体
                .chatRoom(room) // 关联房间实体（可选，但增强关联性）
                .joinTime(LocalDateTime.now())
                .build();
        chatRoomMemberRepository.save(member); // JPA自动保存到数据库

        // 7. 更新在线房间缓存
        redisService.updateOnlineRooms(roomId);

        return roomId; // 返回新创建的房间ID
    }



    /**
     * 分页搜索聊天室（每页10条）
     * @param dto 查询参数（可选字段）
     * @param page 页码（从1开始，符合前端习惯）
     */
    public void searchLobbies(LobbyListDTO dto, int page) {
        int validPage = Math.max(1, page);
        // 1. 开启分页（每页10条，PageHelper会修改后续SQL的LIMIT/OFFSET）
        PageHelper.startPage(validPage, 10);

        // 2. 执行动态查询（返回未分页的List，PageHelper会自动包装成分页结果）
        List<LobbyListVO> voList = chatRoomMapper.searchLobbies(dto);

        // 3. 将PageHelper的PageInfo转换为Spring Data的Page（可选，按需选择返回类型）
        PageInfo<LobbyListVO> pageInfo = new PageInfo<>(voList);
        PageImpl<LobbyListVO> pagedResult = new PageImpl<>(
                pageInfo.getList(),
                PageRequest.of(validPage - 1, 10),
                pageInfo.getTotal()
        );
        // 将稳定的PagedModel推送到订阅主题
        messagingTemplate.convertAndSend("/topic/searchLobbies_result", pagedResult);
    }


    /**
     * 用户加入大厅（群聊房间）
     * @param roomId 房间ID
     * @param sessionId WebSocket会话ID（用于获取用户信息）
     */
    @Transactional // 事务保证原子性：成员添加+房间人数更新
    public void joinChatRoom(String roomId, String sessionId) {
        // 1. 获取当前用户信息（从会话映射）
        Long userId = getUserDetailsBySessionId(sessionId);
        // 2. 获取已持久化的用户实体（避免关联未保存的用户）
        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在：ID=" + userId));

        // 3. 获取目标房间（验证存在性与状态）
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RuntimeException("房间已开始或已结束：ID=" + roomId);
        }
        if (room.getCurrentMembers() >= room.getRequiredMembers()) {
            throw new RoomFullException("房间已满（需" + room.getRequiredMembers() + "人）：ID=" + roomId);
        }

        // 4. 检查用户是否已在房间内（避免重复加入）
        if (chatRoomMemberRepository.existsByChatRoomAndMember(room, user)) {
            throw new UserNotInRoomException("用户已在房间中：ID=" + user.getUserId());
        }

        // 5. 创建成员记录并保存
        ChatRoomMember member = ChatRoomMember.builder()
                .id(new ChatRoomMemberId(user.getUserId(), roomId)) // 复合主键：用户ID+房间ID
                .member(user)
                .chatRoom(room)
                .joinTime(LocalDateTime.now())
                .build();
        chatRoomMemberRepository.save(member);

        // 6. 更新房间当前人数（+1）并保存
        room.setCurrentMembers(room.getCurrentMembers() + 1);
        chatRoomRepository.save(room);

        // 7. 更新Redis在线房间缓存（可选）
        redisService.updateOnlineRooms(roomId);

        log.info("用户[{}]加入房间[{}]成功", user.getUserId(), roomId);
    }


    /**
     * 获取指定群房间的历史消息（分页）
     * @param dto 请求参数（roomId、page、size）
     * @return 分页后的群消息
     */
    public Page<GroupMessage> getGroupMessages(RoomChatHistoryDTO dto) {
        // 1. 参数校验（避免无效查询）
        validateRoomHistoryParams(dto);

        // 2. 构造分页请求（页码从0开始，按消息时间倒序排列）
        Pageable pageable = PageRequest.of(
                dto.getPage(),          // 前端传递的页码（需确保与前端约定一致，若前端从1开始则减1）
                dto.getSize(),          // 每页条数
                Sort.by(Sort.Direction.DESC, "createTime") // 按发送时间倒序（最新消息在前）
        );

        // 3. 调用仓库查询
        return groupMessageRepository.findByRoom_RoomId(dto.getRoomId(), pageable);
    }


    /**
     * 获取指定房间的最新N条消息
     * @param roomId 群房间ID（非空）
     * @param limit 最新消息数量（1~100，避免全表扫描）
     * @return 最新消息列表（按时间倒序）
     */
    public List<GroupMessage> getRecentMessages(
            @NotBlank(message = "房间ID不能为空") String roomId,
            @Min(value = 1, message = "数量至少1条")
            @Max(value = 100, message = "数量最多100条") int limit) {

        // 构造分页请求：第一页（0开始）、每页limit条、按createTime倒序
        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "createTime")
        );

        // 调用仓库查询，返回内容列表（无需分页元数据）
        return groupMessageRepository
                .findByRoom_RoomIdOrderByCreateTimeDesc(roomId, pageable)
                .getContent();
    }

    // 检查房间状态,如果达标可开启游戏
    public void checkRoomStatus(String roomId) {
        ChatRoom room = chatRoomMapper.checkByRoomId(roomId);
        if (room == null) {
            throw new RoomException("房间不存在");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RoomException("房间已开始");
        }
        if (room.getCurrentMembers() < room.getRequiredMembers()) {
            throw new RoomException("房间人数不足");
        }
        if (room.getCurrentMembers() > room.getRequiredMembers()) {
            throw new RoomException("房间人数超出");
        }
        room.setStatus(RoomStatus.ACTIVE);
        chatRoomMapper.updateRoomStatus(room);
        AiChatSession session = AiChatSession.builder().sessionId(UUID.randomUUID().toString())
                .roomId(roomId).build();
        aiChatSessionMapper.insertGroupSession(session);
        log.info("房间{}已激活", roomId);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, "房间已激活");
    }


    // 发送消息
    public void sendLobbyMessage(@Payload SendMessageRequest message,
                                 @Header("simpSessionId") String sessionId) {
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        String  roomId = message.getRoomId();
        // 2. 提取userId（假设CustomUserDetails有getUserId()方法）
        Long userId = userDetails.getUserId();
        //验证发送消息的人是否在房间中
        boolean isMember = isUserInLobby(roomId, userId);
        if (!isMember) {
            throw new RuntimeException("用户未加入该群聊，无法发送消息");
        }
        GroupMessage messageEntity = GroupMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .room(ChatRoom.builder().roomId(roomId).build())
                .sender(UserInfo.builder().userId(userId).build())
                .content(message.getContent())
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createTime(LocalDateTime.now())
                .build();
        groupMessageMapper.insertGroupMessage(messageEntity);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, messageEntity);
    }


    /**
     * 处理消息发送请求（核心入口）
     * @param request 前端传递的消息参数
     * @param sessionId WebSocket会话ID（用于获取发送者身份）
     */
    @Transactional // 事务：保存消息+广播需原子性
    public void sendGameMessage(@Validated({Default.class}) SendMessageRequest request, String sessionId) {
        // 1. 根据chatType区分群聊/私聊
        if ("GROUP".equalsIgnoreCase(request.getChatType())) {
            sendGroupMessage(request, sessionId);
            // }
            // else if ("PRIVATE".equalsIgnoreCase(request.getChatType())) {
            //     sendPrivateMessage(request, sessionId);
        } else {
            throw new IllegalArgumentException("不支持的消息类型：" + request.getChatType());
        }
    }



    /**
     * 用户离开大厅（群聊房间）
     * @param roomId 房间ID
     * @param sessionId WebSocket会话ID（用于获取用户信息）
     */
    @Transactional // 事务保证原子性：成员删除+房间人数更新
    public void leaveLobby(String roomId, String sessionId) {
        // 1. 获取当前用户信息（从会话映射）
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        if (userDetails == null) {
            throw new RuntimeException("用户未登录");
        }
        // 2. 获取已持久化的用户实体
        UserInfo user = userInfoRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在：ID=" + userDetails.getUserId()));

        // 3. 获取目标房间（验证存在性）
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));

        // 4. 查找用户的成员记录（验证是否在房间内）
        ChatRoomMemberId memberId = new ChatRoomMemberId(user.getUserId(), roomId);
        ChatRoomMember member = chatRoomMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + user.getUserId()));

        // 5. 删除成员记录
        chatRoomMemberRepository.delete(member);

        // 6. 更新房间当前人数（-1）并保存
        room.setCurrentMembers(room.getCurrentMembers() - 1);
        chatRoomRepository.save(room);

        // 7. 可选：若房间无人，更新状态为“已取消”
        if (room.getCurrentMembers() == 0) {
            room.setStatus(RoomStatus.CANCELLED); // 需在RoomStatus枚举中添加CANCELLED
            chatRoomRepository.save(room);
            log.info("房间[{}]因无成员自动取消", roomId);
        }

        // 8. 更新Redis在线房间缓存（可选）
        redisService.updateOnlineRooms(roomId);

        log.info("用户[{}]离开房间[{}]成功", user.getUserId(), roomId);
    }



    // 加入聊天室
    // public void joinChatRoom(String lobbyId, String sessionId) {
    //     CustomUserDetails user = sessionUserMap.get(sessionId);
    //     Long userId = user.getUserId();
    //     // 检查房间是否存在且未开始
    //     //TODO使用redis查询
    //     ChatRoom room = chatRoomMapper.checkByRoomIdAndStatus(lobbyId, RoomStatus.WAITING);
    //     if(room == null) throw new RuntimeException("房间不存在或已开始");
    //     // 检查用户是否已在房间
    //     ChatRoomMember member = ChatRoomMember.builder().id(new ChatRoomMemberId(userId, lobbyId)).member(user)
    //             .chatRoom(room).joinTime(LocalDateTime.now()).build();
    //     if(isUserInLobby(lobbyId, member)){
    //         throw new RuntimeException("用户已加入该房间");
    //     }
    //     chatRoomMemberMapper.addMember(member);
    //     // 更新大厅人数
    //     room.setCurrentMembers(room.getCurrentMembers() + 1);
    //     chatRoomMapper.updateRoom(room);
    //     addUserToLobby(lobbyId, member);
    //     log.info("用户{}加入大厅{}", userId, lobbyId);
    //     AiChatMessage aiChatMessage = AiChatMessage.builder()
    //             .role(ChatMessageRole.SYSTEM).content(user.getName() + " 加入了大厅").build();
    //     messagingTemplate.convertAndSend("/topic/chat/" + lobbyId, aiChatMessage);
    // }








    // public String createChatRoom(@Payload CreateRoomRequest request,
    //                              @Header("simpSessionId") String sessionId) {
    //     String roomName = request.getRoomName();
    //     Integer requiredMembers = request.getRequiredMembers();
    //     log.info("用户创建{}人大厅{}，会话ID：{}", requiredMembers, roomName, sessionId);
    //     // 校验人数
    //     if (requiredMembers < 2) throw new IllegalArgumentException("所需人数至少2人");
    //     CustomUserDetails userDetails = sessionUserMap.get(sessionId);
    //     if (userDetails == null) {
    //         log.error("未找到会话对应的用户信息，会话ID：{}", sessionId);
    //         // 返回错误响应（根据业务需求调整）
    //         throw new RuntimeException("创建者不存在");
    //     }
    //
    //     // 2. 提取userId（假设CustomUserDetails有getUserId()方法）
    //     Long creatorId = userDetails.getUserId();
    //     // 生成房间ID
    //     String roomId = UUID.randomUUID().toString();
    //     // 创建房间
    //     UserInfo creator = new UserInfo();
    //     BeanUtil.copyProperties(userDetails, creator);
    //     ChatRoom room = ChatRoom.builder().roomId(roomId).roomName(roomName).creator(creator)
    //             .requiredMembers(requiredMembers).currentMembers(1).status(RoomStatus.WAITING)
    //             .createTime(LocalDateTime.now())
    //             .build();
    //     Integer result = chatRoomMapper.createChatRoom(room);
    //     if (result != 1) throw new RuntimeException("创建房间失败");
    //     redisService.updateOnlineRooms(roomId);
    //     // 创建者加入成员表
    //     ChatRoomMember member = ChatRoomMember.builder().id(new ChatRoomMemberId(creatorId, roomId))
    //             .joinTime(LocalDateTime.now()).build();
    //     chatRoomMemberMapper.joinRoomMember(member);
    //     return roomId;
    // }


    public Long getUserDetailsBySessionId(String sessionId) {
        CustomUserDetails userDetails = sessionUserMap.get(sessionId);
        if (userDetails == null) {
            throw new RuntimeException("用户未登录");
        }
        return userDetails.getUserId();
    }






    // // 用户离开大厅
    // public void leaveLobby(String lobbyId,String sessionId) {
    //     CustomUserDetails userDetails = sessionUserMap.get(sessionId);
    //     ChatRoomMember chatRoomMember = ChatRoomMember.builder().id(new ChatRoomMemberId(userDetails.getUserId(), lobbyId)).build();
    //     Set<ChatRoomMember> members = lobbies.get(lobbyId);
    //     if (members == null){
    //         throw new RuntimeException("该大厅已不存在");
    //     }
    //     members.remove(chatRoomMember);
    //     messagingTemplate.convertAndSend("/topic/chat"+lobbyId, userDetails.getName()+"离开大厅");
    //
    //
    //     if (members.isEmpty()) {
    //         lobbies.remove(lobbyId);
    //     }
    // }




    // 添加用户到大厅（线程安全）
    public void addUserToLobby(String lobbyId, ChatRoomMember user) {
        lobbies.computeIfAbsent(lobbyId, k -> ConcurrentHashMap.newKeySet()).add(user);
    }

    // 检查用户是否在大厅（高效版）
    public boolean isUserInLobby(String lobbyId, ChatRoomMember user) {
        Set<ChatRoomMember> members = lobbies.get(lobbyId);
        return members != null && members.contains(user);
    }

    // 检查用户是否在大厅（根据用户ID版）
    public boolean isUserInLobby(String lobbyId, Long userId) {
        Set<ChatRoomMember> members = lobbies.get(lobbyId);
        return members != null && members.stream().anyMatch(m -> m.getId().getMemberId().equals(userId));
    }


    // 获取大厅所有成员
    public Set<ChatRoomMember> getLobbyMembers(String lobbyId) {
        return lobbies.getOrDefault(lobbyId, Collections.emptySet());
    }










    /**
     * 校验历史消息查询参数
     * @param dto 请求参数
     */
    private void validateRoomHistoryParams(RoomChatHistoryDTO dto) {
        if (!StringUtils.hasText(dto.getRoomId())) {
            throw new IllegalArgumentException("房间ID不能为空");
        }
        if (dto.getPage() < 0) {
            throw new IllegalArgumentException("页码不能小于0");
        }
        // 限制每页最大100条（避免全表扫描）
        if (dto.getSize() <= 0 || dto.getSize() > 100) {
            throw new IllegalArgumentException("每页大小需在1~100之间");
        }
    }






    /**
     * 发送群聊消息
     */
    private void sendGroupMessage(SendMessageRequest request, String sessionId) {
        // 2. 获取发送者（从SecurityContext或Session中提取，需结合项目认证方式）
        UserInfo sender = getCurrentUser(sessionId);
        Assert.notNull(sender, "发送者未登录");

        // 3. 验证发送者是否为群成员
        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("群房间不存在：" + request.getRoomId()));
        Optional<ChatRoomMember> memberOpt = chatRoomMemberRepository.findByChatRoomAndMember(room, sender);
        Assert.isTrue(memberOpt.isPresent(), "你不是该群成员，无法发送消息");

        // 4. 保存群消息到数据库
        GroupMessage groupMessage = GroupMessage.builder()
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType())
                .status(MessageStatus.SENT)
                .createTime(LocalDateTime.now()) // 或用@CreatedDate自动填充
                .build();
        groupMessageRepository.save(groupMessage);

        // 5. 广播消息到群房间Topic（/topic/group/{roomId}）
        // 前端需订阅该Topic才能收到群消息
        messagingTemplate.convertAndSend("/topic/group/" + request.getRoomId(), groupMessage);
    }


    /**
     * 发送私聊消息
     */
    private void sendPrivateMessage(SendMessageRequest request, String sessionId) {
        // 2. 获取发送者和接收者
        UserInfo sender = getCurrentUser(sessionId);
        UserInfo receiver = userInfoRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new IllegalArgumentException("接收者不存在：" + request.getReceiverId()));

        // 3. 保存私聊消息到数据库
        PrivateMessage privateMessage = PrivateMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .messageType(request.getMessageType())
                .status(MessageStatus.SENT)
                .isRead(false) // 初始未读
                .createTime(LocalDateTime.now())
                .build();
        privateMessageRepository.save(privateMessage);

        // 4. 广播消息到接收者的专属Topic（/topic/private/{receiverId}）
        // 接收者需订阅该Topic才能收到私聊消息
        messagingTemplate.convertAndSend("/topic/private/" + receiver.getUserId(), privateMessage);
    }


    /**
     * 从Session中获取当前发送者（需结合项目认证方式实现）
     * 示例：从SecurityContext获取（需处理WebSocket的认证）
     */
    private UserInfo getCurrentUser(String sessionId) {
        // 方式1：若WebSocket连接时绑定了用户身份（如用Spring Security的WebSocket认证）
        // 可从SecurityContextHolder获取：
        // return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 方式2：若用SessionRegistry管理在线用户（推荐）
        // 假设SessionRegistry已注册所有在线用户的sessionId和UserInfo映射：
        // return sessionRegistry.getUserBySessionId(sessionId);

        // 示例简化：返回测试用户（实际需替换为真实逻辑）
        return userInfoRepository.findByUsername("testUser")
                .orElseThrow(() -> new IllegalArgumentException("发送者未找到"));
    }
}