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
import com.guanyu.haigui.utils.LobbyRoomUtils;
import com.guanyu.haigui.utils.RedisServiceUtil;
import com.guanyu.haigui.utils.SessionMapUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    // private final ConcurrentMap<String, Set<Long>> lobbies = new
    // ConcurrentHashMap<>();
    private final RedisServiceUtil redisService;
    private final ChatGameMapper chatGameMapper;
    private final ChatGameMemberMapper chatGameMemberMapper;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserInfoRepository userInfoRepository;
    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ChatGameMsgRepository chatGameMsgRepository;
    private final SessionMapUtil sessionMapUtil;
    private final LobbyRoomUtils lobbyRoomUtils;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final chatGameInvitationRepository chatGameInvitationRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        HaiGuiSoup soup = haiGuiSoupRepository.findById(request.getSoupId())
                .orElseThrow(() -> {
                    log.error("汤ID[{}]未找到对应汤", request.getSoupId());
                    return new RuntimeException("汤不存在");
                });

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
                .needInvite(request.getIsPrivate())
                .haiGuiSoup(soup)
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
        // 1. 根据sessionId获取当前用户（你的原有逻辑）
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new RuntimeException("用户不存在"); // 用户不存在，返回空列表
        }
        // 2. 直接查询用户加入的所有聊天室
        List<ChatGame> membersMemberUserId = chatGameRepository.findByMembers_Member_UserId(userId);
        return membersMemberUserId.stream()
                .map(ChatGameDTO::from)
                .peek(dto -> log.info("DTO 转换结果：{}", dto))
                .collect(Collectors.toList());
    }

    // LobbyService.java
    public PageImpl<LobbyListVO> searchLobbies(LobbyListDTO dto, int page) {
        int validPage = Math.max(1, page);
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
        // LobbyService.java 中的步骤3
        List<MemberSimpleVO> membersList = chatGameMemberMapper.selectMembersByRoomIds(roomIds);
        // 按 roomId 分组为 Map<String, List<MemberSimpleVO>>
        Map<String, List<MemberSimpleVO>> membersMap = membersList.stream()
                .collect(Collectors.groupingBy(MemberSimpleVO::getRoomId));

        // 4. 组装LobbyListVO列表
        List<LobbyListVO> voList = baseList.stream().map(map -> {
            LobbyListVO vo = new LobbyListVO();
            // 填充聊天室核心信息
            vo.setRoomId((String) map.get("room_id"));
            vo.setRoomName((String) map.get("room_name"));
            vo.setRequiredMembers(safeConvertToLong(map.get("required_members")));
            vo.setCurrentMembers(safeConvertToLong(map.get("current_members")));
            vo.setStatus(RoomStatus.valueOf((String) map.get("status")));
            vo.setCreateTime((LocalDateTime) map.get("create_time"));

            // 填充创建者
            CreatorInfoVO creator = new CreatorInfoVO();
            creator.setUserId(safeConvertToLong(map.get("creator_user_id")));
            creator.setUsername((String) map.get("creator_username"));
            creator.setAvatar((String) map.get("creator_avatar"));
            vo.setCreator(creator);

            // // 填充成员列表（从membersMap中取，无则为空Set）
            // List<MemberSimpleVO> members = membersMap.getOrDefault(vo.getRoomId(), Collections.emptyList());
            // if (members != null) {
            //     vo.setMembers(new HashSet<>(members));
            // } else {
            //     // 处理单个对象的情况
            //     List<MemberSimpleVO> newMembers = membersMap.getOrDefault(vo.getRoomId(), Collections.emptyList());
            //     vo.setMembers(new HashSet<>(newMembers));
            // }
            vo.setMembers(membersMap.getOrDefault(vo.getRoomId(), Collections.emptyList()));

            return vo;
        }).collect(Collectors.toList());

        // 5. 转换为Spring Data Page
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(baseList);
        return new PageImpl<>(
                voList,
                PageRequest.of(validPage - 1, 10),
                pageInfo.getTotal());
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
        if (room.getNeedInvite()) {
            if(!chatGameInvitationRepository.existsByChatGameRoomIdAndInviteeUserIdAndStatus(roomId, BaseContext.getCurrentId(), InvitationStatus.PENDING)){
                throw new RuntimeException("当前房间为私人房间,请等待邀请");
            }

        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RuntimeException("房间已开始或已结束：ID=" + roomId);
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
        vo.setStatus(LobbyMemberStatus.JOIN);
        // 7. 更新Redis在线房间缓存（可选）
        redisService.updateOnlineRoomsAndNumbers(roomId, num);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId, vo);
        log.info("用户[{}]加入房间[{}]成功", user.getUserId(), roomId);
        return vo;
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
        Page<GameRoomMessageVO> vo = messages.map(GameRoomMessageVO::from);
        lobbyRoomUtils.addUserToLobby(dto.getRoomId(), BaseContext.getCurrentId());
        // lobbyRoomUtils.broadcastGroupMessageToMembers(vo,dto.getRoomId());
        // 转换为 VO 分页
        return vo;
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
        // messagingTemplate.convertAndSend("/topic/lobbyChat/" + request.getRoomId(),
        // messageVO);
        lobbyRoomUtils.broadcastGroupMessageToMembers(messageVO, request.getRoomId());
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

        // 4. 查找用户的成员记录（验证是否在房间内）
        ChatGameMemberId memberId = new ChatGameMemberId(user.getUserId(), roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + user.getUserId()));

        // 5. 删除成员记录
        chatGameMemberRepository.delete(member);

        // 6. 更新房间当前人数（-1）并保存
        int currentMembers = room.getCurrentMembers() - 1;
        room.setCurrentMembers(currentMembers);
        chatGameRepository.save(room);

        // 7. 可选：若房间无人，更新状态为“已取消”
        if (currentMembers == 0) {
            room.setStatus(RoomStatus.CANCELLED); // 需在RoomStatus枚举中添加CANCELLED
            chatGameRepository.save(room);
            log.info("房间[{}]因无成员自动取消", roomId);
            redisService.deleteOnlineRoomsAndNumbers(roomId, currentMembers);
        } else {
            redisService.updateOnlineRoomsAndNumbers(roomId, currentMembers);
        }
        leaveLobbyVO lobbyVO= new leaveLobbyVO();
        lobbyVO.setUserId(user.getUserId());
        lobbyVO.setUserName(user.getName());
        lobbyVO.setUserAvatar(user.getAvatar());
        lobbyVO.setStatus(LobbyMemberStatus.QUIT);
        // 8. 更新Redis在线房间缓存（可选）
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId,lobbyVO);
        lobbyRoomUtils.leaveLobbyRoom(roomId, user.getUserId());
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
        // 查询房间下的所有成员关联数据
        List<ChatGameMember> members = chatGameMemberRepository.findByIdRoomId(roomId);
        searchAllLobbyMemberVO lobbyMemberVO = new searchAllLobbyMemberVO();

        // 转换为LobbyMemberVO
        List<LobbyMemberVO> memberList = members.stream()
                .map(this::convertToLobbyMemberVO)
                .collect(Collectors.toList());
        lobbyMemberVO.setMemberList(memberList);
        lobbyMemberVO.setMemberNum(memberList.size());
        lobbyMemberVO.setMemberId(roomId);
        return lobbyMemberVO;
    }

    /**
     * 将ChatGameMember转换为LobbyMemberVO
     */
    private LobbyMemberVO convertToLobbyMemberVO(ChatGameMember member) {
        LobbyMemberVO vo = new LobbyMemberVO();
        // 成员基础信息（来自关联的UserInfo）
        vo.setUserId(member.getMember().getUserId());
        vo.setUsername(member.getMember().getUsername());
        vo.setAvatar(member.getMember().getAvatar());
        // 加入时间（来自ChatGameMember）
        vo.setStatus(member.getStatus());
        vo.setJoinTime(member.getJoinTime());
        // 是否是房间创建者（对比成员ID和房间创建者ID）
        vo.setIsCreator(member.getChatGame().getCreator().getUserId().equals(member.getMember().getUserId()));
        return vo;
    }

    public suspendRoomVO suspendRoom(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        // if (!member.getChatGame().getCreator().getUserId().equals(userId)) {
        //     throw new RuntimeException("用户不是房间创建者，无法暂停房间");
        // }
        if(!(room.getStatus() == RoomStatus.WAITING || room.getStatus() == RoomStatus.ACTIVE)){
            throw new RuntimeException("房间已结束，无法挂起");
        }
        member.setStatus(MemberStatus.SUSPENDED);
        chatGameMemberRepository.save(member);
        suspendRoomVO suspendRoomVO = new suspendRoomVO();
        suspendRoomVO.setUserId(userId);
        suspendRoomVO.setRoomId(roomId);
        suspendRoomVO.setStatus(LobbyMemberStatus.SUSPEND);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId,suspendRoomVO);
        return suspendRoomVO;
    }

    public resumeRoomVO returnRoom(String roomId) {
        Long userId = BaseContext.getCurrentId();
        ChatGame room = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("房间不存在：ID=" + roomId));
        ChatGameMemberId memberId = new ChatGameMemberId(userId, roomId);
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
                .orElseThrow(() -> new UserNotInRoomException("用户未在房间中：ID=" + userId));
        if(!(room.getStatus() == RoomStatus.WAITING || room.getStatus() == RoomStatus.ACTIVE)){
            throw new RuntimeException("房间已结束，无法返回");
        }
        member.setStatus(MemberStatus.ONLINE);
        chatGameMemberRepository.save(member);
        resumeRoomVO resumeRoomVO = new resumeRoomVO();
        resumeRoomVO.setUserId(userId);
        resumeRoomVO.setRoomId(roomId);
        resumeRoomVO.setStatus(LobbyMemberStatus.ONLINE);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId,resumeRoomVO);
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
            throw new RuntimeException("房间已结束，无法开始");
        }
        member.setStatus(MemberStatus.READY);
        chatGameMemberRepository.save(member);
        readyVO readyVO = new readyVO();
        readyVO.setMemberId(userId);
        readyVO.setRoomId(roomId);
        readyVO.setStatus(LobbyMemberStatus.ONLINE);
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId,readyVO);
        return readyVO;
    }


    // 检查房间状态,如果达标可开启游戏
    public checkRoomStatusVO checkRoomStatus(String roomId) {
        ChatGame room = chatGameRepository.findById(roomId).orElseThrow(() -> new RoomException("房间不存在"));

        if(!Objects.equals(room.getCreator().getUserId(), BaseContext.getCurrentId())){
            throw new RoomException("您没有权限操作此房间");
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
        chatGameRepository.save(room);
        AiChatSession session = AiChatSession.builder().sessionId(UUID.randomUUID().toString())
                .contextId(roomId).build();
        aiChatSessionRepository.save(session);
        log.info("房间{}已激活", roomId);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(room.getHaiGuiSoup().getSoupId())
                .orElseThrow(() -> new RoomException("汤不存在"));
        checkRoomStatusVO vo = new checkRoomStatusVO();
        vo.setStatus(RoomStatus.ACTIVE);
        vo.setRoomId(roomId);
        vo.setSoupSurface(soup.getSoupSurface());
        simpMessagingTemplate.convertAndSend("/topic/memberChange"+roomId, vo);
        return vo;
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
        ChatGameMember member = chatGameMemberRepository.findById(memberId)
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
        // 校验1：只有房主可以邀请成员（creator_id == 当前用户ID）
        if (!chatGame.getCreator().getUserId().equals(currentUserId)) {
            throw new RoomException("只有房主有权邀请成员加入房间");
        }
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
                        .isRead(false)  // 初始为未读
                        .build();
                System.out.println("privateMessage = " + privateMessage);


                privateMessageRepository.save(privateMessage);

                // 创建并添加到返回列表
                InvitationVO vo = InvitationVO.fromEntity(invitation);
                invitationVOs.add(vo);

                // -------------------------- 6. 向被邀请者发送实时邀请通知 --------------------------
                simpMessagingTemplate.convertAndSendToUser(
                        String.valueOf(inviteeId), // 目标用户ID（Stomp会自动拼接成/user/{inviteeId}/private-messages）
                        "/private-messages", // 订阅路径（客户端需要订阅此路径才能收到消息）
                        vo // 要发送的消息内容（邀请记录）
                );

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
}