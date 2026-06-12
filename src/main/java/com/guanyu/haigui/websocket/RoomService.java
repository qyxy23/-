package com.guanyu.haigui.websocket;

import cn.hutool.core.lang.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guanyu.haigui.Enum.*;
import com.guanyu.haigui.Exception.*;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.mapper.ChatGameMapper;
import com.guanyu.haigui.mapper.ChatGameMemberMapper;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.PlayQuotaService;
import com.guanyu.haigui.service.ServicesImpl.SoupPlayabilityService;
import com.guanyu.haigui.service.ServicesImpl.SoupQuestionServiceImpl;
import com.guanyu.haigui.service.UserChatSessionService;
import com.guanyu.haigui.service.VoteTimeoutService;
import com.guanyu.haigui.utils.GameSessionResolver;
import com.guanyu.haigui.utils.GameSessionResolver;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.guanyu.haigui.utils.SessionMapUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class RoomService {
    // 存储大厅ID与成员用户ID的映射（线程安全，仅存用户ID减少内存占用）
    private final RedisServiceUtil redisService;
    private final UserChatSessionService userChatSessionService;
    private final ChatGameMapper chatGameMapper;
    private final ChatGameMemberMapper chatGameMemberMapper;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final StompUserPushService stompUserPushService;
    private final UserInfoRepository userInfoRepository;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ChatGameMsgRepository chatGameMsgRepository;
    private final SessionMapUtil sessionMapUtil;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final HaiGuiGameProgressRepository haiGuiGameProgressRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final chatGameInvitationRepository chatGameInvitationRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameSessionRepository gameSessionRepository;
    private final HaiGuiVoteRecordRepository haiGuiVoteRecordRepository;
    private final HaiGuiVoteSessionRepository haiGuiVoteSessionRepository;
    private final SoupQuestionServiceImpl soupQuestionService;
    private final VoteTimeoutService voteTimeoutService;
    private final GameSessionResolver gameSessionResolver;
    private final PlayQuotaService playQuotaService;
    private final SoupPlayabilityService soupPlayabilityService;


    /**
     * 创建聊天室
     */
    @Transactional // 事务：保证房间创建+成员添加的原子性
    public String createChatRoom(CreateRoomRequest request) {
        String roomName = request.getRoomName();
        Integer requiredMembers = request.getRequiredMembers();

        // 1. 校验基础规则：所需人数≥2
        if (requiredMembers < 2) {
            throw new IllegalArgumentException("所需人数至少2人");
        }

        // 3. 获取**已持久化的创建者实体**
        Long creatorId = BaseContext.getCurrentId();
        UserInfo creator = userInfoRepository.findById(creatorId)
                .orElseThrow(() -> {
                    log.error("用户ID[{}]未找到对应用户", creatorId);
                    return new RuntimeException("创建者不存在");
                });

        HaiGuiSoup soup = soupPlayabilityService.requirePlayableSoup(request.getSoupId());

        // 4. 生成唯一房间ID
        String roomId = UUID.randomUUID().toString();

        // 5. 构建并保存ChatRoom实体（关联已存在的创建者）
        ChatGame game = ChatGame.builder()
                .roomId(roomId)
                .roomName(roomName)
                .creator(creator) // 关联已持久化的UserInfo
                .requiredMembers(requiredMembers)
                .currentMembers(1) // 初始成员数=创建者自己
                .status(RoomStatus.WAITING)
                .createTime(LocalDateTime.now())
                .haiGuiSoup(soup)
                .privacyType(request.getIsPrivate() ? ChatGame.PrivacyType.PRIVATE : ChatGame.PrivacyType.PUBLIC)
                .build();
        chatGameRepository.save(game); // JPA自动保存到数据库

        // 6. 构建并保存ChatGameMember实体（创建者加入房间）
        ChatGameMember member = ChatGameMember.builder()
                .id(new ChatGameMemberId(creatorId, roomId)) // 复合主键：用户ID+房间ID
                .member(creator) // 关联创建者实体
                .chatGame(game) // 关联房间实体（可选，但增强关联性）
                .joinTime(LocalDateTime.now())
                .status(MemberStatus.ONLINE)
                .build();
        chatGameMemberRepository.save(member); // JPA自动保存到数据库

        // 7. 更新在线房间缓存
        redisService.updateOnlineRoomsAndNumbers(roomId, 1);
        // 将房间ID发送到用户的个人主题，以便前端接收
        // 使用simpSessionId确保消息发送到正确的用户会话
        // 注意：convertAndSendToUser方法会自动添加/user/前缀，所以路径只需要/queue/lobbyCreated
        log.info("用户创建{}人大厅[{}]成功，房间ID：{},对应的海龟汤{}", requiredMembers, roomName, roomId,soup.getSoupTitle());
        return roomId;
    }

    /**
     * 获取当前用户加入的大厅列表
     */
    public List<ChatGameDTO> getMineLobbies() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new RuntimeException("用户不存在");
        }

        // 定义需要查询的状态集合
        List<RoomStatus> targetStatuses = Arrays.asList(
                RoomStatus.WAITING,
                RoomStatus.ACTIVE,
                RoomStatus.VOTING
        );

        // 查询用户参与的、状态在目标范围内的房间
        List<ChatGame> userGames = chatGameRepository.findByMembers_Member_UserIdAndStatusIn(
                userId,
                targetStatuses
        );

        return userGames.stream()
                .map(ChatGameDTO::from)
                .peek(dto -> log.info("DTO 转换结果：{}", dto))
                .collect(Collectors.toList());
    }

    // LobbyService.java
    public PageImpl<LobbyListVO> searchLobbies(LobbyListDTO dto, int page) {
        if (dto == null) {
            dto = new LobbyListDTO();
        }
        if (dto.getExcludeInvited() == null) {
            dto.setExcludeInvited(true); // 默认排除邀请房间
        }

        int validPage = Math.max(1, page);
        try {
            PageHelper.startPage(validPage, 10); // 分页（每页10条）

            // 1. 分页查询聊天室核心信息（不关联成员）
            List<Map<String, Object>> baseList = chatGameMapper.searchLobbiesBase(dto);
            if (baseList.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), PageRequest.of(validPage - 1, 10), 0);
            }

            // 2. 收集所有聊天室的room_id（去重）
            Set<String> roomIds = baseList.stream()
                    .map(map -> (String) map.get("room_id"))
                    .collect(Collectors.toSet());

            // 3. 批量查询这些房间的成员列表（返回Map<roomId, List<MemberSimpleVO>>）
            List<MemberSimpleVO> membersList = chatGameMemberMapper.selectMembersByRoomIds(roomIds);
            Map<String, List<MemberSimpleVO>> membersMap = membersList.stream()
                    .collect(Collectors.groupingBy(MemberSimpleVO::getRoomId));

            // 4. 组装LobbyListVO列表
            List<LobbyListVO> voList = baseList.stream().map(map -> {
                LobbyListVO vo = new LobbyListVO();
                vo.setRoomId((String) map.get("room_id"));
                vo.setRoomName((String) map.get("room_name"));
                vo.setRequiredMembers(safeConvertToLong(map.get("required_members")));
                vo.setCurrentMembers(safeConvertToLong(map.get("current_members")));
                vo.setStatus(RoomStatus.valueOf((String) map.get("status")));
                vo.setCreateTime((LocalDateTime) map.get("create_time"));

                CreatorInfoVO creator = new CreatorInfoVO();
                creator.setUserId(safeConvertToLong(map.get("creator_user_id")));
                creator.setUsername((String) map.get("creator_username"));
                creator.setAvatar((String) map.get("creator_avatar"));
                vo.setCreator(creator);
                vo.setMembers(membersMap.getOrDefault(vo.getRoomId(), Collections.emptyList()));

                return vo;
            }).collect(Collectors.toList());

            // 5. 转换为Spring Data Page
            PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(baseList);
            return new PageImpl<>(
                    voList,
                    PageRequest.of(validPage - 1, 10),
                    pageInfo.getTotal());
        } finally {
            PageHelper.clearPage();
        }
    }

    private Long safeConvertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValue();
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    @Transactional // 事务保证原子性：成员添加+房间人数更新
    public joinChatRoomVO joinChatRoom(String roomId) {
        // 1. 获取当前用户信息
        UserInfo user = userInfoRepository.findById(BaseContext.getCurrentId()).orElseThrow(() -> new RuntimeException("用户不存在"));

        // 3. 获取目标房间（验证存在性与状态）
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        if (room.getPrivacyType() == ChatGame.PrivacyType.PRIVATE) {
            if(!chatGameInvitationRepository.existsByChatGameRoomIdAndInviteeUserIdAndStatus(roomId, BaseContext.getCurrentId(), InvitationStatus.PENDING)){
                throw new BusinessException(405,"当前房间为私人房间,请等待邀请");
            }
        }
        if (room.getStatus() == RoomStatus.WAITING) {
            soupPlayabilityService.assertCanJoinWaitingRoom(room.getHaiGuiSoup());
        }
        if(!(room.getStatus()==RoomStatus.WAITING||room.getStatus()==RoomStatus.ACTIVE||room.getStatus()==RoomStatus.VOTING)){
            throw new BusinessException(403, "房间已结束或取消，无法操作");
        }
        if (room.getCurrentMembers() >= room.getRequiredMembers()) {
            throw new RoomFullException("房间已满（需" + room.getRequiredMembers() + "人）：ID=" + roomId);
        }

        // 4. 检查用户是否已在房间内（避免重复加入）
        if (chatGameMemberRepository.existsByChatGameAndMember(room, user)) {
            throw new UserNotInRoomException("用户已在房间中：ID=" + user.getUserId());
        }

        // 5. 创建成员记录并保存
        ChatGameMember member = ChatGameMember.builder()
                .id(new ChatGameMemberId(user.getUserId(), roomId)) // 复合主键：用户ID+房间ID
                .member(user)
                .chatGame(room)
                .status(MemberStatus.ONLINE)
                .joinTime(LocalDateTime.now())
                .build();
        chatGameMemberRepository.save(member);

        // 6. 更新房间当前人数（+1）并保存
        int num = room.getCurrentMembers() + 1;
        room.setCurrentMembers(num);
        chatGameRepository.save(room);

        joinChatRoomVO vo = new joinChatRoomVO();
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUsername());
        vo.setUserAvatar(user.getAvatar());
        vo.setChatType(MessageChatType.GAME_JOIN);
        // 7. 更新Redis在线房间缓存（可选）
        redisService.updateOnlineRoomsAndNumbers(roomId, num);
        broadcastMemberChange(roomId, MessageChatType.GAME_JOIN, user.getUserId(), user.getUsername(), user.getAvatar());
        log.info("用户[{}]加入房间[{}]成功", user.getUserId(), roomId);
        return vo;
    }

    /**
     * 事务提交后再推送成员变动，并附带最新成员列表快照，避免订阅方读到未提交数据
     */
    private void broadcastMemberChange(String roomId, MessageChatType chatType, Long userId, String userName, String userAvatar) {
        broadcastMemberChange(roomId, chatType, userId, userName, userAvatar, null);
    }

    private void broadcastMemberChange(String roomId, MessageChatType chatType, Long userId, String userName, String userAvatar, String eventStatus) {
        Runnable publish = () -> {
            LobbyMemberChangeVO message = new LobbyMemberChangeVO();
            message.setChatType(chatType);
            message.setUserId(userId);
            message.setUserName(userName);
            message.setUserAvatar(userAvatar);
            message.setRoomId(roomId);
            message.setEventStatus(eventStatus);
            message.setMemberSnapshot(getAllMembersByRoomId(roomId));
            simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, message);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    /**
     * 获取指定群房间的历史消息（分页）
     * 
     * @param dto 请求参数（roomId、page、size）
     * @return 分页后的群消息
     */
    public Page<GameRoomMessageVO> getGameMessages(RoomChatHistoryDTO dto) {
        validateRoomHistoryParams(dto);

        // 构造分页请求（页码从 0 开始，按 createTime 倒序）
        Pageable pageable = PageRequest.of(
                dto.getPage(),
                dto.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        // 调用 Repository 方法（使用带关联查询的方法名）
        Page<ChatGameMessage> messages = chatGameMsgRepository
                .findByRoomIdWithAssociations(dto.getRoomId(), pageable);

        // 遍历打印消息详情（可选，用于调试）
        // List<ChatGameMessage> messageList = messages.getContent();
        // 转换为 VO 分页
        return messages.map(GameRoomMessageVO::from);
    }

    /**
     * 获取指定房间的最新N条消息
     * 
     * @param roomId 群房间ID（非空）
     * @param limit  最新消息数量（1~100，避免全表扫描）
     * @return 最新消息列表（按时间倒序）
     */
    public List<GameRoomMessageVO> getRecentMessages(String roomId, int limit) {
        validateGetRecentMessagesParams(roomId, limit);

        // 构造分页请求（仅取第 1 页，数量为 limit，倒序）
        Pageable pageable = PageRequest.of(
                0, // 第 1 页（从 0 开始）
                limit, // 每页条数
                Sort.by(Sort.Direction.DESC, "createTime"));

        // 调用 Repository 方法（使用命名规则的排序查询）
        Page<ChatGameMessage> messagePage = chatGameMsgRepository
                .findByChatGame_RoomIdOrderByCreateTimeDesc(roomId, pageable);

        // 提取内容列表
        List<ChatGameMessage> messages = messagePage.getContent();

        // 转换为 VO 列表
        return messages.stream()
                .map(GameRoomMessageVO::from)
                .collect(Collectors.toList());
    }

    private void validateGetRecentMessagesParams(String roomId, int limit) {
        if (roomId == null || roomId.isEmpty()) {
            throw new IllegalArgumentException("房间ID不能为空");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("限制数量超出范围（1~100）");
        }
    }


    public void sendLobbyMessage(SendGameRoomMsgRequest request, String sessionId) {
        // 1. 获取当前登录用户（发送者）
        UserInfo sender = userInfoRepository.findById(sessionMapUtil.getUserIdBySessionId(sessionId))
                .orElseThrow(() -> new BusinessException(403, "用户不存在"));

        // 2. 校验群聊是否存在
        ChatGame room = chatGameRepository.findById(request.getRoomId())
                .orElseThrow(() -> new BusinessException(403, "群聊不存在：" + request.getRoomId()));

        // 3. 构造群聊消息实体
        ChatGameMessage message = ChatGameMessage.builder()
                .chatGame(room) // 关联群聊
                .sender(sender) // 关联发送者（当前用户）
                .content(request.getContent()) // 消息内容
                .messageType(request.getMessageType()) // 消息类型
                .status(MessageStatus.SENT) // 默认状态：已发送
                .createTime(LocalDateTime.now())
                .build();
        chatGameMsgRepository.save(message);

        // 4. 广播消息到大厅聊天主题，让所有用户都能实时看到消息
        GameRoomMessageVO messageVO = GameRoomMessageVO.from(message);
        messageVO.setChatType(MessageChatType.LOBBY_MESSAGE); // 确保设置chatType为LOBBY_MESSAGE
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+request.getRoomId(), messageVO);
        log.info("用户[{}]在大厅[{}]发送消息并广播", sender.getUsername(), request.getRoomId());
    }

    /**
     * 
     * 用户离开大厅（群聊房间）
     * @param roomId    房间ID
     */
    public leaveLobbyVO leaveLobby(String roomId) {
        // 1. 获取当前用户信息（从会话映射）
        // 2. 获取已持久化的用户实体
        UserInfo user = userInfoRepository.findById(BaseContext.getCurrentId())
                .orElseThrow(() -> new RuntimeException("用户不存在：ID=" + BaseContext.getCurrentId()));
        if (user == null) {
            throw new RuntimeException("用户未登录");
        }


        // 3. 获取目标房间（验证存在性）
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));

        if(room.getStatus()==RoomStatus.FINISHED||room.getStatus()==RoomStatus.CANCELLED){
            throw new BusinessException(403, "房间已结束或取消，无法操作");
        }

        // 4. 查找用户的成员记录（验证是否在房间内）
        ChatGameMemberId memberId = new ChatGameMemberId(user.getUserId(), roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + user.getUserId()));
        // 5. 删除成员记录
        chatGameMemberRepository.delete(member);

        if (Objects.equals(room.getCreator().getUserId(), user.getUserId())) {
            // 加锁避免并发修改（同一房间仅允许一个线程处理转移）
            synchronized (roomId.intern()) {
                // 4. 筛选符合条件的新主人：在线/已准备 + 按加入时间排序（最早加入优先）
                List<ChatGameMember> eligibleMembers = chatGameMemberRepository
                        .findByIdRoomIdAndStatusInOrderByJoinTimeAsc( // 自定义查询：按房间ID+状态筛选+按加入时间升序
                                roomId,
                                Arrays.asList(MemberStatus.ONLINE, MemberStatus.READY) // 筛选状态：在线/已准备
                        );

                if (eligibleMembers.size()>=2) {
                    // 选第一个符合条件的成员作为新房主（最早加入的在线成员）
                    ChatGameMember newOwner = eligibleMembers.get(1);
                    // 更新房间的创建者为新房主（直接用newOwner的member_id，即sys_user的user_id）
                    UserInfo newOwnerInfo = userInfoRepository.findById(newOwner.getId().getMemberId())
                            .orElseThrow(() -> new RuntimeException("新房主用户信息不存在"));
                    room.setCreator(newOwnerInfo);
                    chatGameRepository.save(room);

                    // 5. 记录日志与通知新房主
                    log.info("房主[{}]离开，转移创建人身份给[{}]（用户ID：{}）",
                            user.getName(), newOwnerInfo.getName(), newOwnerInfo.getUserId());

                    broadcastMemberChange(roomId, null, newOwnerInfo.getUserId(), newOwnerInfo.getUsername(), newOwnerInfo.getAvatar(), LobbyMemberStatus.BECOME_OWNER.name());
                } else {
                    // 没有合适的新主人，房间自动取消（延续原逻辑）
                    room.setStatus(RoomStatus.CANCELLED);
                    chatGameRepository.save(room);
                    log.info("房间[{}]因无合适新房主自动取消", roomId);
                }
            }
        }

        // 6. 更新房间当前人数（-1）并保存
        int currentMembers = room.getCurrentMembers() - 1;
        room.setCurrentMembers(currentMembers);
        chatGameRepository.save(room);

        // 7. 可选：若房间无人，更新状态为“已取消”
        if (currentMembers == 0) {
            log.info("房间[{}]因无成员自动取消", roomId);
            redisService.deleteOnlineRoomsAndNumbers(roomId);
        } else {
            redisService.updateOnlineRoomsAndNumbers(roomId, currentMembers);
        }
        leaveLobbyVO lobbyVO= new leaveLobbyVO();
        lobbyVO.setUserId(user.getUserId());
        lobbyVO.setUserName(user.getName());
        lobbyVO.setUserAvatar(user.getAvatar());
        lobbyVO.setChatType(MessageChatType.GAME_QUIT);
        broadcastMemberChange(roomId, MessageChatType.GAME_QUIT, user.getUserId(), user.getName(), user.getAvatar());
        log.info("用户[{}]离开房间[{}]成功", user.getName(), roomId);
        return lobbyVO;
    }



    /**
     * 校验历史消息查询参数
     * 
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



    public searchAllLobbyMemberVO getAllMembersByRoomId(String roomId) {
        List<ChatGameMember> members = chatGameMemberRepository.findByRoomIdWithMemberAndGame(roomId);
        ChatGame room = members.stream()
                .map(ChatGameMember::getChatGame)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> chatGameRepository.findById(roomId)
                        .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId)));

        searchAllLobbyMemberVO lobbyMemberVO = new searchAllLobbyMemberVO();
        List<LobbyMemberVO> memberList = members.stream()
                .map(member -> convertToLobbyMemberVO(member, room))
                .collect(Collectors.toList());
        lobbyMemberVO.setMemberList(memberList);
        lobbyMemberVO.setMemberNum(memberList.size());
        lobbyMemberVO.setMemberId(roomId);
        lobbyMemberVO.setMaxMembers(room.getRequiredMembers());
        return lobbyMemberVO;
    }

    /**
     * 将ChatGameMember转换为LobbyMemberVO
     */
    private LobbyMemberVO convertToLobbyMemberVO(ChatGameMember member, ChatGame room) {
        LobbyMemberVO vo = new LobbyMemberVO();
        vo.setUserId(member.getMember().getUserId());
        vo.setUsername(member.getMember().getUsername());
        vo.setAvatar(member.getMember().getAvatar());
        vo.setStatus(member.getStatus());
        vo.setJoinTime(member.getJoinTime());
        Long creatorId = room.getCreator() != null ? room.getCreator().getUserId() : null;
        vo.setIsCreator(creatorId != null && creatorId.equals(member.getMember().getUserId()));
        return vo;
    }

    public suspendRoomVO suspendRoom(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        if(room.getStatus() == RoomStatus.FINISHED || room.getStatus() == RoomStatus.CANCELLED){
            throw new RuntimeException("房间已结束，无法挂起");
        }
        member.setStatus(MemberStatus.SUSPENDED);
        chatGameMemberRepository.save(member);
        suspendRoomVO suspendRoomVO = new suspendRoomVO();
        suspendRoomVO.setUserId(userId);
        suspendRoomVO.setRoomId(roomId);
        suspendRoomVO.setChatType(MessageChatType.SUSPEND_ROOM);
        broadcastMemberChange(roomId, MessageChatType.SUSPEND_ROOM, userId, null, null);
        return suspendRoomVO;
    }

    public resumeRoomVO returnRoom(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        if (!(room.getStatus() == RoomStatus.WAITING
                || room.getStatus() == RoomStatus.ACTIVE
                || room.getStatus() == RoomStatus.VOTING)) {
            throw new RuntimeException("房间已结束，无法返回");
        }
        // 游戏进行中挂起后返回应恢复为「游戏中」，而非一律「在线」
        MemberStatus restoredStatus = room.getStatus() == RoomStatus.WAITING
                ? MemberStatus.ONLINE
                : MemberStatus.IN_GAME;
        member.setStatus(restoredStatus);
        chatGameMemberRepository.save(member);
        resumeRoomVO resumeRoomVO = new resumeRoomVO();
        resumeRoomVO.setUserId(userId);
        resumeRoomVO.setRoomId(roomId);
        resumeRoomVO.setChatType(MessageChatType.RETURN_ROOM);
        broadcastMemberChange(roomId, MessageChatType.RETURN_ROOM, userId, null, null);
        return resumeRoomVO;
    }

    public readyVO ready(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        if(!(room.getStatus() == RoomStatus.WAITING || room.getStatus() == RoomStatus.ACTIVE)){
            throw new RuntimeException("房间已结束，无法准备");
        }
        member.setStatus(MemberStatus.READY);
        chatGameMemberRepository.save(member);
        readyVO readyVO = new readyVO();
        readyVO.setUserId(userId);
        readyVO.setRoomId(roomId);
        readyVO.setChatType(MessageChatType.READY_ROOM);
        broadcastMemberChange(roomId, MessageChatType.READY_ROOM, userId, null, null);
        return readyVO;
    }

    /**
     * 取消准备
     */
    public cancelReadyVO cancelReady(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        if(room.getStatus() != RoomStatus.WAITING){
            throw new RuntimeException("房间已开始或结束，无法取消准备");
        }
        member.setStatus(MemberStatus.ONLINE);
        chatGameMemberRepository.save(member);
        cancelReadyVO cancelReadyVO = new cancelReadyVO();
        cancelReadyVO.setUserId(userId);
        cancelReadyVO.setRoomId(roomId);
        cancelReadyVO.setChatType(MessageChatType.CANCEL_READY_ROOM);
        broadcastMemberChange(roomId, MessageChatType.CANCEL_READY_ROOM, userId, null, null);
        return cancelReadyVO;
    }


    // 检查房间状态,如果达标可开启游戏
    public CheckRoomStatusVO checkRoomStatus(String roomId) {
        ChatGame room = chatGameRepository.findById(roomId).orElseThrow(() -> new RoomException("房间不存在"));
        List<ChatGameMember> members = chatGameMemberRepository.findByIdRoomId(roomId);
        if(!Objects.equals(room.getCreator().getUserId(), BaseContext.getCurrentId())){
            return CheckRoomStatusVO.error(403, "您没有权限操作此房间");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            return CheckRoomStatusVO.error(1, "房间已开始或结束");
        }
        if (room.getCurrentMembers() < room.getRequiredMembers()) {
            return CheckRoomStatusVO.error(2, "房间人数不足");
        }
        if (room.getCurrentMembers() > room.getRequiredMembers()) {
            return CheckRoomStatusVO.error(3, "房间人数超出");
        }
        for (ChatGameMember member : members){
            if (member.getStatus() != MemberStatus.READY) {
                CheckRoomStatusVO vo = CheckRoomStatusVO.error(1, "房间里有人未准备");
                simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId,vo);
                return vo;
            }
        }
        playQuotaService.assertCanStartNewGame(room.getCreator().getUserId());
        HaiGuiSoup soup = haiGuiSoupRepository.findById(room.getHaiGuiSoup().getSoupId()).orElseThrow(() -> new RoomException("汤不存在"));
        AiChatSession session = new AiChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setTitle(room.getRoomName());
        session.setContextType(ChatContextType.GAME_ROOM);
        session.setContextId(roomId);
        session.setUserId(room.getCreator().getUserId());
        aiChatSessionRepository.save(session);
        GameSession gameSession = new GameSession();
        gameSession.setSessionId(UUID.randomUUID().toString());
        gameSession.setSoupId(room.getHaiGuiSoup().getSoupId());
        gameSession.setUserId(room.getCreator().getUserId());
        gameSession.setPlayMode(PlayMode.MULTI);
        gameSession.setRoomId(roomId);
        gameSession.setChatSessionId(session.getSessionId());
        gameSession.setStatus(GameSession.GameSessionStatus.ONGOING);
        gameSession.setRemainingQuestions(soup.getDefaultMaxQuestions());
        gameSessionRepository.saveAndFlush(gameSession);
        room.setStartTime(LocalDateTime.now());
        room.setStatus(RoomStatus.ACTIVE);
        room.setGameSessionId(gameSession.getSessionId());
        chatGameRepository.save(room);
        String soupId = room.getHaiGuiSoup().getSoupId();
        List<InferenceTask> tasks = inferenceTaskRepository.findBySoupId(soupId);
        for (ChatGameMember member : members){
            member.setStatus(MemberStatus.IN_GAME);
        }
        chatGameMemberRepository.saveAll(members);
        // 2. 为每个任务创建进度记录
        List<HaiGuiGameProgress> progresses = tasks.stream()
                .map(task -> {
                    HaiGuiGameProgress progress = new HaiGuiGameProgress();
                    progress.setGameSessionId(gameSession.getSessionId());
                    progress.setTaskId(task.getTaskId());
                    progress.setCompleted(false);
                    progress.setTriggeredFragmentIds(new HashSet<>()); // 初始空数组
                    progress.setCompletionTime(null);
                    return progress;
                })
                .collect(Collectors.toList());

        // 3. 批量保存
        haiGuiGameProgressRepository.saveAll(progresses);

        log.info("房间任务初始化完成: roomId={}, soupId={}, 任务数={}",
                roomId, soupId, tasks.size());
        log.info("房间{}已激活", roomId);
        CheckRoomStatusVO vo = CheckRoomStatusVO.success(roomId, gameSession.getSessionId(), room.getStatus(), soup.getSoupSurface());
        vo.setMemberSnapshot(getAllMembersByRoomId(roomId));
        String pushRoomId = roomId;
        publishAfterCommit(() -> {
            CheckRoomStatusVO pushVo = CheckRoomStatusVO.success(
                    pushRoomId, gameSession.getSessionId(), RoomStatus.ACTIVE, soup.getSoupSurface());
            pushVo.setMemberSnapshot(getAllMembersByRoomId(pushRoomId));
            simpMessagingTemplate.convertAndSend("/topic/memberChange" + pushRoomId, pushVo);
        });
        return vo;
    }

    private void publishAfterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    public String CancelRoom(String roomId) {
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("房间不存在"));
        room.setStatus(RoomStatus.WAITING);
        chatGameRepository.save(room);
        return "取消成功";
    }

    /**
     * 邀请用户加入房间
     */
    public List<InvitationVO> invite(InvitationDto request) {
        Long currentUserId = BaseContext.getCurrentId();
        String roomId = request.getRoomId();
        List<Long> inviteeIds = request.getInviteeIds();

        // 验证当前用户是否在房间中
        ChatGameMemberId memberId = new ChatGameMemberId(currentUserId, roomId);
        chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + currentUserId));

        // 验证房间存在性
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404,"房间不存在"));

        // 验证当前用户存在性（作为邀请者）
        UserInfo inviter = userInfoRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(404,"邀请者不存在"));

        HaiGuiSoup soup = haiGuiSoupRepository.findById(chatGame.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new BusinessException(404,"汤不存在"));

        // -------------------------- 3. 权限与状态校验 --------------------------
        // 校验1：已在房间中的任意成员均可邀请（见上方 memberId 校验）
        // 校验2：房间状态是否允许邀请（已结束/已取消的房间无法邀请）
        if (chatGame.getStatus() == RoomStatus.FINISHED || chatGame.getStatus() == RoomStatus.CANCELLED) {
            throw new RoomException("房间已结束或取消，无法发起邀请");
        }

        List<InvitationVO> invitationVOs = new ArrayList<>();

        // -------------------------- 4. 为每个被邀请者创建邀请记录 --------------------------
        for (Long inviteeId : inviteeIds) {
            try {
                // 验证被邀请者存在性
                UserInfo invitee = userInfoRepository.findById(inviteeId)
                        .orElseThrow(() -> new BusinessException(404,"被邀请者不存在，用户ID：" + inviteeId));

                // 校验3：被邀请者是否已在房间中（避免重复邀请）
                boolean isAlreadyMember = chatGameMemberRepository.existsByChatGameAndMember(chatGame, invitee);
                if (isAlreadyMember) {
                    log.warn("用户[{}]已在房间[{}]中，跳过邀请", inviteeId, roomId);
                    continue;
                }

                // 校验4：是否存在未处理的邀请（避免重复发送）
                boolean hasPendingInvitation = chatGameInvitationRepository.existsByChatGameRoomIdAndInviteeUserIdAndStatus(
                        roomId, inviteeId, InvitationStatus.PENDING);
                if (hasPendingInvitation) {
                    log.warn("用户[{}]已有房间[{}]的未处理邀请，跳过邀请", inviteeId, roomId);
                    continue;
                }

                // 创建邀请记录
                ChatGameInvitation invitation = new ChatGameInvitation();
                // 关联房间、邀请者、被邀请者
                invitation.setInvitationId(UUID.randomUUID().toString());
                invitation.setChatGame(chatGame);
                invitation.setInviter(inviter);
                invitation.setInvitee(invitee);  // 被邀请者
                // 状态初始化为PENDING（待接受）
                invitation.setStatus(InvitationStatus.PENDING);
                // 保存到数据库
                chatGameInvitationRepository.save(invitation);

                // -------------------------- 5. 创建邀请卡片消息记录并保存到私人对话中 --------------------------

                // 使用 HashMap 替代 JSONObject
                Map<String, Object> content = new HashMap<>();
                content.put("id", roomId);
                content.put("name", chatGame.getRoomName());
                content.put("coverUrl", soup.getSoupAvatar());

                // 如果你需要将这个 Map 转换成 JSON 字符串保存到 content 字段：
                String contentJson = objectMapper.writeValueAsString(content);


                PrivateMessage privateMessage = PrivateMessage.builder()
                        .messageId(UUID.randomUUID().toString())
                        .sender(inviter)  // 邀请者作为发送者
                        .receiver(invitee)  // 被邀请者作为接收者
                        .content(contentJson)  // 邀请消息内容（简化版）
                        .messageType(MessageType.INVITATION)  // 消息类型
                        .status(MessageStatus.SENT)  // 状态为已发送
                        .build();
                System.out.println("privateMessage = " + privateMessage);


                privateMessageRepository.save(privateMessage);
                // 异步更改缓存中最后消息的信息
                asyncAfterSendMessage(currentUserId, inviteeId);

                // 创建并添加到返回列表
                InvitationVO vo = InvitationVO.fromEntity(invitation);
                invitationVOs.add(vo);

                // -------------------------- 6. 向被邀请者发送实时邀请通知 --------------------------
                stompUserPushService.pushPrivateChannel(inviteeId, vo);

                log.info("用户[{}]成功邀请好友[{}]加入房间[{}]，已保存海龟汤邀请消息记录", currentUserId, inviteeId, roomId);

            } catch (Exception e) {
                log.error("邀请用户[{}]加入房间[{}]时发生错误: {}", inviteeId, roomId, e.getMessage());
                // 继续处理其他被邀请者，不中断整个邀请流程
            }
        }

        if (invitationVOs.isEmpty()) {
            throw new RoomException("没有成功发送任何邀请，请检查被邀请者状态");
        }

        return invitationVOs;
    }

    @Async("taskExecutor")
    public void asyncAfterSendMessage(Long userId, Long receiverId) {
        String content = "邀请你一起玩海龟汤";
        String senderName = userInfoRepository.findById(userId).map(UserInfo::getUsername).orElse("");
        userChatSessionService.onPrivateMessageSent(
                userId, receiverId, content, LocalDateTime.now(), senderName);
    }


    public VoteEndGameVO voteEndGame(String roomId) {
        Long currentUserId = BaseContext.getCurrentId();
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("房间不存在，房间ID：" + roomId));
        if(chatGame.getStatus()==RoomStatus.VOTING){
            List<HaiGuiVoteSession> sessions = haiGuiVoteSessionRepository.findByGameSessionIdAndStatusOrderByCreatedAtDesc(
                    chatGame.getGameSessionId(), HaiGuiVoteSession.VoteStatus.ONGOING);

            if (!sessions.isEmpty()) {
                HaiGuiVoteSession currentSession = sessions.get(0); // 最新创建的会话

                // 3. 检查投票是否超时
                if (LocalDateTime.now().isAfter(currentSession.getEndTime())) {
                    voteTimeoutService.expireVoteIfOverdue(chatGame, currentSession, VoteTimeoutService.NotifyPolicy.PASSIVE_QUERY);
                } else {
                    return VoteEndGameVO.error("当前房间有正在进行中的投票");
                }
            }
        }
        if(chatGame.getStatus()!=RoomStatus.ACTIVE){
            return VoteEndGameVO.error("当前房间未开始或已结束");
        }
        GameSession gameSession = gameSessionRepository.findById(chatGame.getGameSessionId())
                .orElseThrow(() -> new RoomException("游戏会话不存在，房间ID：" + roomId));
        chatGame.setStatus(RoomStatus.VOTING);
        chatGameRepository.save(chatGame);
        HaiGuiVoteSession voteSession = new HaiGuiVoteSession();
        voteSession.setVoteSessionId(UUID.randomUUID().toString());
        voteSession.setGameSessionId(gameSession.getSessionId());
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(5);
        voteSession.setEndTime(endTime);
        voteSession.setInitiatorId(currentUserId);
        voteSession.setStatus(HaiGuiVoteSession.VoteStatus.ONGOING);
        voteSession.setIsDeleted(false);
        voteSession.setCreatedAt(LocalDateTime.now());
        voteSession.setAgreedVotes(1);
        //TODO:如果有人掉线，那么再次上线时按理来说是不应该允许投票的，但是目前没有处理，会抢占其他人的投票机会
        voteSession.setTotalVoters(chatGame.getCurrentMembers());
        haiGuiVoteSessionRepository.saveAndFlush(voteSession);

        HaiGuiVoteRecord voteRecord = new HaiGuiVoteRecord();
        voteRecord.setVoteSessionId(voteSession.getVoteSessionId());
        voteRecord.setVoteOption(HaiGuiVoteRecord.VoteOption.AGREE);
        voteRecord.setUserId(currentUserId);
        voteRecord.setIsDeleted(false);
        haiGuiVoteRecordRepository.save(voteRecord);


        // VoteCheckMessage delayedMsg = VoteCheckMessage.createDelayedCheck(
        //         voteSession.getVoteSessionId(), 5
        // );
        // voteMQProducer.sendDelayedCheck(delayedMsg, 9); // 延迟级别9 = 5分钟


        VoteEndGameVO voteEndGameVO = VoteEndGameVO.success(voteSession.getTotalVoters(),endTime,1);
        voteEndGameVO.setChatType(MessageChatType.START_VOTING);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId, voteEndGameVO);
        return voteEndGameVO;
    }

    public VoteEndGameVO continueVote(String roomId, HaiGuiVoteRecord.VoteOption voteOption) {
        Long currentUserId = BaseContext.getCurrentId();

        // 1. 验证房间存在且处于投票状态
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("房间不存在，房间ID：" + roomId));

        if(chatGame.getStatus()==RoomStatus.FINISHED){
            return VoteEndGameVO.error("当前房间已结束");
        }else if(chatGame.getStatus()==RoomStatus.WAITING){
            return VoteEndGameVO.error("当前房间未开始");
        }


        if (chatGame.getStatus() != RoomStatus.VOTING) {
            return VoteEndGameVO.error("当前没有进行中的投票");
        }

        // 2. 获取当前进行中的投票会话（取最新创建的）
        List<HaiGuiVoteSession> sessions = haiGuiVoteSessionRepository.findByGameSessionIdAndStatusOrderByCreatedAtDesc(
                chatGame.getGameSessionId(), HaiGuiVoteSession.VoteStatus.ONGOING);

        if (sessions.isEmpty()) {
            return VoteEndGameVO.error("未找到进行中的投票会话");
        }
        HaiGuiVoteSession currentSession = sessions.get(0); // 最新创建的会话

        // 3. 检查投票是否超时
        if (LocalDateTime.now().isAfter(currentSession.getEndTime())) {
            voteTimeoutService.expireVoteIfOverdue(chatGame, currentSession, VoteTimeoutService.NotifyPolicy.PASSIVE_QUERY);
            return VoteEndGameVO.error("投票已超时，请发起新投票");
        }

        // 4. 检查用户是否已在本次投票中投过票
        if (haiGuiVoteRecordRepository.existsByVoteSessionIdAndUserId(
                currentSession.getVoteSessionId(), currentUserId)) {
            return VoteEndGameVO.error("您已经投过票了");
        }

        // 5. 创建投票记录
        HaiGuiVoteRecord voteRecord = new HaiGuiVoteRecord();
        voteRecord.setVoteSessionId(currentSession.getVoteSessionId());
        voteRecord.setUserId(currentUserId);
        voteRecord.setVoteOption(voteOption);
        voteRecord.setIsDeleted(false);
        voteRecord.setCreatedAt(LocalDateTime.now());
        haiGuiVoteRecordRepository.save(voteRecord);

        // 更新同意票数
        if(voteOption==HaiGuiVoteRecord.VoteOption.AGREE&&currentSession.getAgreedVotes()<currentSession.getTotalVoters()){
            currentSession.incrementAgreedVotes();
            haiGuiVoteSessionRepository.save(currentSession);
        }

        // //  发送即时检查消息
        // sendImmediateCheckMessage(currentSession.getVoteSessionId());

        // 返回最新统计信息
        VoteEndGameVO voteEndGameVO = VoteEndGameVO.success(currentSession.getTotalVoters(),currentSession.getEndTime(),currentSession.getAgreedVotes());
        voteEndGameVO.setChatType(MessageChatType.CONTINUE_VOTING);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId, voteEndGameVO);

        checkVoteResult(currentSession);

        // 8. 返回最新统计信息
        return voteEndGameVO;
    }

    // private void sendImmediateCheckMessage(String voteSessionId) {
    //     VoteCheckMessage voteCheckMessage = VoteCheckMessage.createImmediateCheck(voteSessionId);
    //     voteMQProducer.sendImmediateCheck(voteCheckMessage);
    // }

    public void processTimeoutVoteCheck(VoteCheckMessage message) {
        HaiGuiVoteSession session = haiGuiVoteSessionRepository.findById(message.getVoteSessionId())
                .orElseThrow(() -> new RuntimeException("投票会话不存在"));

        // 如果仍在投票中，则强制结束
        if (session.getStatus() == HaiGuiVoteSession.VoteStatus.ONGOING) {
            session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
            haiGuiVoteSessionRepository.save(session);

            // 恢复房间状态
            restoreRoomStatus(gameSessionResolver.requireRoomId(session.getGameSessionId()));
        }
    }

    // 检查投票结果
    //投票人数要大于80%且同意比例要大于60%
    private void checkVoteResult(HaiGuiVoteSession session) {

        // 如果投票已结束，不再处理
        if (session.getStatus() != HaiGuiVoteSession.VoteStatus.ONGOING) {
            return;
        }

        // 获取房间所有成员
        int totalMembers = session.getTotalVoters();

        // 获取投票记录
        int agreeCount = session.getAgreedVotes();

        // 计算所需票数
        int requiredVotes = calculateRequiredVotes(totalMembers);
        // 判断是否达到结束条件
        // 检查是否达到要求
        // 如果同意比例达到要求，则结束游戏
        if (agreeCount >= requiredVotes) {
            session.setStatus(HaiGuiVoteSession.VoteStatus.PASSED);
            haiGuiVoteSessionRepository.save(session);

            // 结束游戏
            soupQuestionService.endGame(gameSessionResolver.requireRoomId(session.getGameSessionId()),
                    GameEndReason.VOTE_PASSED);
        } else if (agreeCount == totalMembers) {
            // 所有成员已投票但未通过
            session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
            haiGuiVoteSessionRepository.save(session);

            // 恢复房间状态
            restoreRoomStatus(gameSessionResolver.requireRoomId(session.getGameSessionId()));
        }
    }




    // 恢复房间状态
    private void restoreRoomStatus(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        chatGame.setStatus(RoomStatus.ACTIVE);
        chatGameRepository.save(chatGame);
    }


    /**
     * 根据房间人数计算结束游戏所需的最少同意票数
     * @param playerCount 房间人数
     * @return 所需最少同意票数
     */
    private int calculateRequiredVotes(int playerCount) {
        if (playerCount <= 0) {
            throw new IllegalArgumentException("房间人数必须大于0");
        }
        // 根据规则映射表
        return switch (playerCount) {
            case 2, 3 -> 2;
            case 4 -> 3;
            case 5, 6 -> 4;
            case 7 -> 5;
            case 8 -> 6;
            case 9 -> 7;
            case 10 -> 8;
            default -> {
                // 超过10人的处理规则（可根据需要调整）
                if (playerCount > 10) {
                    yield playerCount - 2;
                }
                // 少于2人的异常情况
                yield 1;
            }
        };
    }


}