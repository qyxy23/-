package com.guanyu.haigui.websocket;

import com.guanyu.haigui.Enum.*;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.Projection.ChatGroupMemberProjection;
import com.guanyu.haigui.Projection.ChatGroupMemberWithRole;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.service.ChatMessageRetentionService;
import com.guanyu.haigui.service.ChatSendIdempotencyService;
import com.guanyu.haigui.service.UserChatSessionService;
import com.guanyu.haigui.utils.GroupRoomUtils;
import com.guanyu.haigui.utils.CiImageAuditService;
import com.guanyu.haigui.utils.CosUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class GroupService {
    private final ChatGroupRepository chatGroupRepository;
    private final ChatGroupMemberRepository chatGroupMemberRepository;
    private final FriendRelationRepository friendRelationsRepository;
    private final UserInfoRepository sysUserRepository; // 操作`sys_user`表的Repository（需自行定义）
    private final ModelMapper modelMapper;
    private final ChatGroupMessageRepository chatGroupMessageRepository;
    private final SimpMessagingTemplate simpMessagingTemplate; // WebSocket消息模板
    private final StompUserPushService stompUserPushService;
    private final GroupJoinRequestRepository joinRequestRepo;
    private final UserInfoRepository userInfoRepository;
    private final UserChatSessionService userChatSessionService;
    // private static final int LATEST_MESSAGES_LIMIT = 20;
    private final GroupRoomUtils groupRoomUtils;
    private final CosUtil cosUtil;
    private final CiImageAuditService ciImageAuditService;
    @PersistenceContext
    private EntityManager entityManager;
    private final ChatGroupAdminRepository chatGroupAdminRepository;
    private final ChatMessageRetentionService chatMessageRetentionService;
    private final ChatSendIdempotencyService chatSendIdempotencyService;


    // /**
    //  * 查询群成员详细信息
    //  * @param request 请求参数（群ID、发送者ID列表）
    //  * @return 群成员详情列表
    //  * @throws BusinessException 无权限或数据不存在异常
    //  */
    // public List<GroupMemberDetailVO> getGroupMembersDetail(GroupMembersDetailDTO request) throws BusinessException {
    //     String groupId = request.getGroupId();
    //     List<Long> senderIds = request.getSenderIds();
    //     Long currentUserId = request.getCurrentUserId();
    //
    //     // 1. 权限校验：当前用户必须是群成员
    //     if (!isGroupMember(groupId, currentUserId)) {
    //         log.warn("用户[{}]无权限查询群[{}]的成员信息", currentUserId, groupId);
    //         throw new BusinessException(403,"无权限查询该群成员信息");
    //     }
    //
    //     // 2. 批量查询群成员信息（过滤掉不在该群的发送者）
    //     List<ChatGroupMember> members = batchGetGroupMembers(groupId, senderIds);
    //     if (CollectionUtils.isEmpty(members)) {
    //         log.info("群[{}]中未找到对应的成员：{}", groupId, senderIds);
    //         return List.of(); // 返回空列表而非null
    //     }
    //
    //     // 3. 转换为VO（适配前端展示）
    //     return members.stream()
    //             .map(this::convertToVO)
    //             .collect(Collectors.toList());
    // }


    /** 批量查询群成员 */
    private List<ChatGroupMember> batchGetGroupMembers(String groupId, List<Long> senderIds) {
        return chatGroupMemberRepository.findByIdGroupIdAndIdMemberIdIn(groupId, senderIds);
    }

    private boolean isGroupMember(String groupId, Long userId) {
        return chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, userId);
    }


    /**
     * 用户申请加入群聊
     */
    @Transactional
    public GroupJoinNotification applyJoinGroup(JoinGroupRoomRequest request) {
        Long userId = BaseContext.getCurrentId();
        String groupId = request.getGroupRoomId();
        String description = request.getDescription();
        // 1. 检查是否已提交待处理申请
        Optional<GroupJoinRequest> existingRequest = joinRequestRepo.findByUserUserIdAndGroupGroupIdAndStatus(
                userId, groupId, RequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            throw new BusinessException(403, "您已提交过加群申请，请等待处理");
        }

        // 2. 验证群是否存在
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(403, "群不存在"));

        // 3. 创建加群申请记录
        GroupJoinRequest joinRequest = new GroupJoinRequest();
        joinRequest.setUser(userInfoRepository.findById(userId).orElseThrow(() -> new BusinessException(403, "用户不存在")));
        joinRequest.setGroup(group);
        joinRequest.setDescription(description);
        joinRequest.setApplyTime(LocalDateTime.now());
        joinRequestRepo.save(joinRequest);

        // 4. 通知群主（WebSocket推送）
        return sendJoinNotificationToOwner(group, joinRequest);
    }

    /**
     * 向群主推送加群申请通知（WebSocket）
     */
    private GroupJoinNotification sendJoinNotificationToOwner(ChatGroup group, GroupJoinRequest request) {
        // 构建通知消息
        GroupJoinNotification notification = new GroupJoinNotification();
        notification.setRequestId(request.getId());
        notification.setApplicantName(request.getUser().getUsername());
        notification.setDescription(request.getDescription());
        notification.setGroupName(group.getGroupName());
        notification.setChatType(MessageChatType.GROUP_JOIN_REQUESTS);
        notification.setStatus(RequestStatus.PENDING);
        notification.setApplyTime(request.getApplyTime());

        // 发送给群主（群主订阅此主题：/topic/group/{groupId}/join-requests）
        pushToUserPrivateChannel(group.getCreator().getUserId(), notification);
        return notification;
    }

    /**
     * 创建群聊（拉好友）
     *
     * @param request 请求参数（好友列表）
     * @return 群ID
     */
    @Transactional // 事务保证原子性：群创建失败则成员不添加
    public String createGroupRoom(CreateGroupRequest request) {
        // 1. 获取当前用户ID（需自行实现SecurityUtils）
        Long currentUserId = BaseContext.getCurrentId();

        // 2. 验证好友关系（必须是已接受的好友）
        validateFriendRelations(currentUserId, request.getFriendIds());

        // 3. 创建群聊实体（无需设置头像/名称，数据库默认空值）
        ChatGroup newGroup = buildChatGroup(currentUserId);
        chatGroupRepository.save(newGroup);
        UserInfo sysUser = sysUserRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(403, "用户不存在"));

        ChatGroupAdministrator ownerAdmin = new ChatGroupAdministrator();
        ownerAdmin.setId(new ChatGroupAdministratorId(newGroup.getGroupId(), currentUserId));
        ownerAdmin.setUser(sysUser);
        ownerAdmin.setChatGroup(newGroup);
        ownerAdmin.setIsOwner(true);
        chatGroupAdminRepository.save(ownerAdmin);
        addUserToGroup(newGroup.getGroupId(), currentUserId);
        request.getFriendIds().forEach(friendId -> addUserToGroup(newGroup.getGroupId(), friendId));

        return newGroup.getGroupId();
    }

    /**
     * 群成员邀请好友直接入群（无需审批）
     *
     * @return 实际加入人数
     */
    @Transactional(rollbackFor = Exception.class)
    public int inviteFriendsToGroup(InviteGroupFriendsRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        String groupId = request.getGroupId();

        if (!chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, currentUserId)) {
            throw new BusinessException(403, "您不是群成员，无法邀请好友");
        }

        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(404, "群聊不存在"));

        List<Long> friendIds = request.getFriendIds().stream().distinct().toList();
        validateFriendRelations(currentUserId, friendIds);

        UserInfo inviter = userInfoRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(403, "用户不存在"));

        List<Long> addedIds = new ArrayList<>();
        for (Long friendId : friendIds) {
            if (chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, friendId)) {
                continue;
            }
            addUserToGroup(groupId, friendId);
            addedIds.add(friendId);

            pushToUserPrivateChannel(
                    friendId,
                    new JoinGroupRoomVO(
                            friendId,
                            groupId,
                            group.getGroupName(),
                            group.getGroupAvatar(),
                            JoinGroupStatus.AGREE,
                            MessageChatType.GROUP_AGREE_REQUESTS));
        }

        if (addedIds.isEmpty()) {
            throw new BusinessException(400, "所选好友已在群中");
        }

        for (Long addedId : addedIds) {
            UserInfo addedUser = userInfoRepository.findById(addedId)
                    .orElseThrow(() -> new BusinessException(403, "被邀请用户不存在"));
            GroupMemberJoinVO joinVO = new GroupMemberJoinVO();
            joinVO.setRoomId(groupId);
            joinVO.setUserId(addedId);
            joinVO.setUserName(addedUser.getUsername());
            joinVO.setOperatorId(currentUserId);
            joinVO.setOperatorName(inviter.getUsername());
            joinVO.setChatType(MessageChatType.GROUP_MEMBER_JOIN);
            broadcastGroupMessageToMembers(joinVO, groupId);
        }

        log.info("用户 {} 邀请 {} 加入群 {}", currentUserId, addedIds, groupId);
        return addedIds.size();
    }

    private void validateFriendRelations(Long userId, List<Long> friendIds) {
        if (CollectionUtils.isEmpty(friendIds)) {
            throw new BusinessException(403, "好友列表不能为空");
        }

        // 查询「所有与当前用户相关的好友关系」（包括主动添加和被添加）
        List<FriendRelation> relations = friendRelationsRepository
                .findByUserUserIdOrFriendUserId(userId, userId); // 获取当前用户参与的所有关系

        // 提取有效好友ID：当前用户与friendIds中的用户是「双向ACCEPTED」关系
        Set<Long> validFriendIds = new HashSet<>();

        // 场景1：当前用户是「发起方」（user_user_id=userId），好友是friend_user_id
        List<Long> initiatedFriendIds = relations.stream()
                .filter(rel -> rel.getUser().getUserId().equals(userId) && // 当前用户是发起方
                        rel.getStatus() == FriendStatus.ACCEPTED && // 关系已接受
                        friendIds.contains(rel.getFriend().getUserId()) // 好友在请求列表中
                )
                .map(rel -> rel.getFriend().getUserId())
                .toList();

        // 场景2：当前用户是「被添加方」（friend_user_id=userId），好友是user_user_id
        List<Long> receivedFriendIds = relations.stream()
                .filter(rel -> rel.getFriend().getUserId().equals(userId) && // 当前用户是被添加方
                        rel.getStatus() == FriendStatus.ACCEPTED && // 关系已接受
                        friendIds.contains(rel.getUser().getUserId()) // 好友在请求列表中
                )
                .map(rel -> rel.getUser().getUserId())
                .toList();

        // 合并两种场景的有效ID（去重）
        validFriendIds.addAll(initiatedFriendIds);
        validFriendIds.addAll(receivedFriendIds);

        // 检查请求的好友ID是否都在有效集合中
        List<Long> invalidIds = friendIds.stream()
                .filter(id -> !validFriendIds.contains(id))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new BusinessException(403, "以下用户不是您的好友：" + invalidIds);
        }
    }

    /**
     * 构建群聊实体（默认值：头像空、名称可自动生成）
     */
    private ChatGroup buildChatGroup(Long creatorId) {
        return ChatGroup.builder()
                .groupId(generateGroupId()) // 生成UUID群ID
                .groupName("群聊-" + generateShortUuid()) // 可选：自动生成群名称（如不需要可注释）
                .creator(sysUserRepository.findById(creatorId).orElseThrow(() -> new BusinessException(403, "创建者不存在")))
                .groupAvatar("") // 默认空头像（符合数据库设计）
                .build();
    }

    /**
     * 添加用户到群聊（避免重复添加）
     */
    private void addUserToGroup(String groupId, Long memberId) {
        if (chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, memberId)) {
            return; // 已存在则跳过
        }

        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(403, "群聊不存在"));
        ChatGroupMemberId memberIdObj = new ChatGroupMemberId(memberId, groupId);
        ChatGroupMember member = ChatGroupMember.builder()
                .id(memberIdObj)
                .member(sysUserRepository.findById(memberId).orElseThrow(() -> new BusinessException(403, "用户不存在")))
                .chatGroup(group)
                .joinTime(LocalDateTime.now())
                .build();

        chatGroupMemberRepository.save(member);
        groupRoomUtils.addUserToGroup(groupId, memberId);
        userChatSessionService.ensureGroupSession(
                memberId, groupId, group.getGroupName(), group.getGroupAvatar());
    }

    /**
     * 生成UUID群ID（36位）
     */
    private String generateGroupId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * 生成短UUID（用于群名称，可选）
     */
    private String generateShortUuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 获取当前用户已加入的群聊
     *
     * @return 群聊DTO列表
     */
    @PreAuthorize("isAuthenticated()") // 仅认证用户可访问
    public List<ChatGroupVo> getMineGroupRooms() {
        // 1. 获取当前登录用户的ID（从Spring Security上下文）
        Long userId = BaseContext.getCurrentId();

        // 2. 查询用户加入的群
        List<ChatGroup> joinedGroups = chatGroupRepository.findJoinedGroupsByUserId(userId);

        // 3. 转换为DTO（过滤不需要的字段，适配前端）
        return joinedGroups.stream()
                .map(group -> modelMapper.map(group, ChatGroupVo.class))
                .collect(Collectors.toList());
    }

    /**
     * 搜索群聊（按名字模糊查询+动态排序+分页）
     *
     * @param dto  查询参数（群名、排序字段、排序方向）
     * @param page 页码（从0开始）
     * @return 分页后的群聊VO列表
     */
    public PageImpl<GroupRoomListVO> searchLobbies(CharGroupListDTO dto, int page) {
        String groupName = dto.getGroupName();
        String sortField = dto.getSortField();
        String sortOrder = dto.getSortOrder();
        int pageSize = dto.getPageSize();

        // ------------------------------
        // 1. 构建动态查询（Criteria API）
        // ------------------------------
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        // 查询结果：ChatGroup实体 + 成员数（Long）
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ChatGroup> cg = cq.from(ChatGroup.class); // 群聊根实体
        Join<ChatGroup, ChatGroupMember> cgm = cg.join("members", JoinType.LEFT); // 左连接群成员（统计人数）

        // 选择字段：ChatGroup + 成员数（COUNT(cgm)）
        cq.select(cb.array(cg, cb.count(cgm)));

        // 条件：群名模糊查询（若为空则忽略）
        if (StringUtils.isNotBlank(groupName)) {
            cq.where(cb.like(cg.get("groupName"), "%" + groupName + "%"));
        }

        // 分组：按群ID唯一分组（避免重复统计）
        cq.groupBy(cg.get("groupId"));

        // 排序：根据前端传的字段动态构建
        Order order = buildOrder(cb, cg, cgm, sortField, sortOrder);
        cq.orderBy(order);

        // 执行查询（分页）
        TypedQuery<Object[]> query = entityManager.createQuery(cq);
        query.setFirstResult((int) PageRequest.of(page, pageSize).getOffset()); // 偏移量
        query.setMaxResults(pageSize); // 每页数量
        List<Object[]> resultList = query.getResultList();

        // ------------------------------
        // 2. 查询总条数（去重群数量）
        // ------------------------------
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<ChatGroup> countCg = countCq.from(ChatGroup.class);
        countCq.select(cb.countDistinct(countCg)); // 统计唯一群数量
        if (StringUtils.isNotBlank(groupName)) {
            countCq.where(cb.like(countCg.get("groupName"), "%" + groupName + "%"));
        }
        Long total = entityManager.createQuery(countCq).getSingleResult();

        // ------------------------------
        // 3. 转换为VO列表
        // ------------------------------
        List<GroupRoomListVO> voList = resultList.stream()
                .map(result -> convertToVO((ChatGroup) result[0], (Long) result[1]))
                .collect(Collectors.toList());

        // ------------------------------
        // 4. 构造分页结果（PageImpl）
        // ------------------------------
        // 用前端传的 sortOrder 转换为 Spring Data 的 Direction
        Sort.Direction direction = parseSortDirection(sortOrder);
        // 用前端传的 sortField 转换为实体属性名（比如 memberCount → members）
        String sortFieldEntity = getOrderField(sortField);
        // 构造 Spring Data 的 Sort
        Sort sort;
        if (sortFieldEntity != null && !sortFieldEntity.trim().isEmpty()) {
            sort = Sort.by(direction, sortFieldEntity);
        } else {
            // 如果排序字段无效，使用默认排序
            sort = Sort.by(Sort.Direction.DESC, "createTime");
        }
        // 构造 Pageable
        Pageable pageable = PageRequest.of(page, pageSize, sort);
        return new PageImpl<>(voList, pageable, total);
    }

    private Sort.Direction parseSortDirection(String sortOrder) {
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            return Sort.Direction.DESC; // 默认降序
        }

        try {
            return Sort.Direction.fromString(sortOrder.trim());
        } catch (IllegalArgumentException e) {
            return Sort.Direction.DESC; // 解析失败时使用默认值
        }
    }

    /**
     * 构建动态排序条件
     */
    private Order buildOrder(CriteriaBuilder cb, Root<ChatGroup> cg, Join<ChatGroup, ChatGroupMember> cgm,
            String sortField, String sortOrder) {
        // 处理 sortOrder 为空或无效的情况，设置默认值
        Sort.Direction direction;
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            direction = Sort.Direction.DESC; // 默认降序
        } else {
            try {
                direction = Sort.Direction.fromString(sortOrder);
            } catch (IllegalArgumentException e) {
                direction = Sort.Direction.DESC; // 如果解析失败，使用默认值
            }
        }
        return switch (sortField) {
            case "memberCount" -> // 按成员数排序（统计值）
                direction.isAscending()
                        ? cb.asc(cb.count(cgm))
                        : cb.desc(cb.count(cgm));
            case "createTime" -> // 按创建时间排序（实体字段）
                direction.isAscending()
                        ? cb.asc(cg.get("createTime"))
                        : cb.desc(cg.get("createTime"));
            default -> // 默认按创建时间降序
                cb.desc(cg.get("createTime"));
        };
    }

    /**
     * 获取排序字段的实体属性名（用于Pageable）
     */
    private String getOrderField(String sortField) {
        return "memberCount".equals(sortField) ? "members" : sortField;
    }

    /**
     * 将查询结果（ChatGroup + 成员数）转换为VO
     */
    private GroupRoomListVO convertToVO(ChatGroup group, Long memberCount) {
        return GroupRoomListVO.builder()
                .groupId(group.getGroupId())
                .groupAvatar(group.getGroupAvatar())
                .groupName(group.getGroupName())
                .creatorName(group.getCreator().getUsername())
                .memberCount(memberCount)
                .createTime(group.getCreateTime())
                .build();
    }

    /**
     * 获取指定群聊的历史消息（仅分页，无排序）
     *
     * @param dto 查询参数（群ID、分页）
     * @return 分页后的群消息VO列表
     */
    public Page<GroupMessageVO> getGroupMessages(GroupChatHistoryDTO dto) {
        // 内联构建查询条件：过滤群ID + 预加载关联实体
        Specification<GroupMessage> spec = (root, query, cb) -> {
            // 过滤条件：消息所属群ID等于目标群
            return cb.equal(root.get("chatGroup").get("groupId"), dto.getGroupId());
        };
        Pageable pageable = PageRequest.of(dto.getPage(), dto.getSize());
        // 执行查询（返回GroupMessage分页）
        Page<GroupMessage> messagePage = chatGroupMessageRepository.findAll(spec, pageable);

        groupRoomUtils.addUserToGroup(dto.getGroupId(), BaseContext.getCurrentId());
        // 转换为GroupMessageVO分页
        return messagePage.map(GroupMessageVO::from);
    }

    /** 离开群聊页时清零当前用户在该群的未读数 */
    public void clearGroupUnread(String groupId) {
        userChatSessionService.clearGroupUnread(BaseContext.getCurrentId(), groupId);
    }

    /** 增量拉取群聊消息（afterTime 之后，升序） */
    @Transactional(readOnly = true)
    public List<GroupMessageVO> getGroupMessagesAfter(ChatMessagesAfterDTO dto) {
        String groupId = dto.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId 不能为空");
        }
        Long userId = BaseContext.getCurrentId();
        int size = dto.getSize() != null ? Math.min(Math.max(dto.getSize(), 1), 100) : 50;
        LocalDateTime afterTime = dto.getAfterTime() != null ? dto.getAfterTime() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime clearAt = userChatSessionService.getSession(userId, groupId, "GROUP")
                .map(ChatSessionVO::getHistoryClearAt)
                .orElse(null);
        if (clearAt != null && clearAt.isAfter(afterTime)) {
            afterTime = clearAt;
        }
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "createTime"));
        return chatGroupMessageRepository.findMessagesAfterInGroup(groupId, afterTime, pageable)
                .stream()
                .map(GroupMessageVO::from)
                .collect(Collectors.toList());
    }

    /** 清空群聊聊天记录（账号级边界） */
    public void clearGroupChatHistory(String groupId) {
        userChatSessionService.clearChatHistory(BaseContext.getCurrentId(), groupId, "GROUP");
    }

    public List<GroupMessageVO> getRecentMessages(String groupId, int limit) {
        // 1. 按群ID过滤，按发送时间降序，取前limit条
        Pageable pageable = PageRequest.of(0, limit);
        Page<GroupMessage> messages = chatGroupMessageRepository
                .findByChatGroup_GroupIdOrderByCreateTimeDesc(groupId, pageable);

        // 2. 转换为前端VO
        return messages.stream()
                .map(GroupMessageVO::from) // GroupMessage→GroupMessageVO
                .collect(Collectors.toList());
    }

    public GroupMessageVO sendGroupRoomMessage(SendGroupMessageRequest request) {
        Long userID = BaseContext.getCurrentId();
        // 1. 验证发送者是否为群成员
        boolean isMember = chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(
                request.getGroupId(), userID);
        if (!isMember) {
            throw new BusinessException(403, "您不是群成员，无法发送消息");
        }

        // 2. 获取群和发送者实体（避免懒加载异常）
        ChatGroup group = chatGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new BusinessException(403, "群不存在"));
        UserInfo sender = userInfoRepository.findById(userID)
                .orElseThrow(() -> new BusinessException(403, "用户不存在"));

        String clientMsgId = chatSendIdempotencyService.normalizeClientMsgId(request.getClientMsgId());
        if (clientMsgId != null) {
            var existing = chatSendIdempotencyService.findExistingGroup(
                    request.getGroupId(), userID, clientMsgId);
            if (existing.isPresent()) {
                return toGroupMessageResponse(existing.get(), request.getGroupId(), userID, false);
            }
        }

        // 3. 创建并保存消息
        GroupMessage message = new GroupMessage();
        message.setChatGroup(group);
        message.setSender(sender);
        message.setClientMsgId(clientMsgId);
        message.setContent(request.getContent());
        message.setMessageType(request.getMessageType());
        message.setCreateTime(LocalDateTime.now());
        message.setStatus(MessageStatus.SENT);
        try {
            chatGroupMessageRepository.save(message);
        } catch (DataIntegrityViolationException ex) {
            if (clientMsgId == null) {
                throw ex;
            }
            GroupMessage duplicate = chatSendIdempotencyService
                    .findExistingGroup(request.getGroupId(), userID, clientMsgId)
                    .orElseThrow(() -> ex);
            return toGroupMessageResponse(duplicate, request.getGroupId(), userID, false);
        }
        chatMessageRetentionService.trimGroupConversation(request.getGroupId());
        userChatSessionService.onGroupMessageSent(
                request.getGroupId(),
                userID,
                message.getContent(),
                message.getCreateTime(),
                sender.getUsername());
        return toGroupMessageResponse(message, request.getGroupId(), userID, true);
    }

    private GroupMessageVO toGroupMessageResponse(
            GroupMessage message,
            String groupId,
            Long senderUserId,
            boolean broadcast) {
        GroupMessageVO vo = GroupMessageVO.from(message);
        vo.setChatType(MessageChatType.GROUP_MESSAGE);
        if (broadcast) {
            broadcastGroupMessageToMembers(vo, groupId, senderUserId);
        }
        return vo;
    }

    @Transactional // 保证所有操作原子性（要么全成，要么全回滚）
    public void leaveGroupRoom(String groupId) {
        Long userId = BaseContext.getCurrentId();
        Optional<ChatGroupMemberProjection> memberOpt = chatGroupMemberRepository
                .findByMemberUserIdAndChatGroupGroupId(userId, groupId);

        if (memberOpt.isEmpty()) {
            throw new BusinessException(403, "您未加入该群，无法退出");
        }

        if (isUserGroupOwner(groupId, userId)) {
            dissolveGroup(groupId, userId);
        } else {
            removeMemberFromGroup(groupId, userId);
        }

        leaveGroupRoomVO leaveVO = new leaveGroupRoomVO();
        leaveVO.setUserId(userId);
        leaveVO.setRoomId(groupId);
        leaveVO.setChatType(MessageChatType.GROUP_LEAVE);
        broadcastGroupMessageToMembers(leaveVO, groupId);
    }

    /**
     * 群主/管理员将成员移出群聊
     */
    @Transactional(rollbackFor = Exception.class)
    public void kickGroupMember(String groupId, Long targetUserId) {
        Long operatorId = BaseContext.getCurrentId();

        if (targetUserId == null) {
            throw new BusinessException(400, "请指定要移出的成员");
        }
        if (operatorId.equals(targetUserId)) {
            throw new BusinessException(403, "不能移出自己");
        }
        if (!chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, operatorId)) {
            throw new BusinessException(403, "您不是群成员，无权操作");
        }
        if (!chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, targetUserId)) {
            throw new BusinessException(403, "该用户不是群成员");
        }
        if (isUserGroupOwner(groupId, targetUserId)) {
            throw new BusinessException(403, "不能移出群主");
        }

        boolean operatorIsOwner = isUserGroupOwner(groupId, operatorId);
        boolean operatorIsAdmin = isUserGroupAdmin(groupId, operatorId);
        if (!operatorIsOwner && !operatorIsAdmin) {
            throw new BusinessException(403, "只有群主或管理员可以移出成员");
        }
        if (!operatorIsOwner && isUserGroupAdmin(groupId, targetUserId)) {
            throw new BusinessException(403, "管理员不能移出其他管理员");
        }

        UserInfo targetUser = userInfoRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(403, "被移出用户不存在"));
        UserInfo operator = userInfoRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException(403, "操作者不存在"));

        removeMemberFromGroup(groupId, targetUserId);

        kickGroupRoomVO kickVO = new kickGroupRoomVO();
        kickVO.setRoomId(groupId);
        kickVO.setUserId(targetUserId);
        kickVO.setUserName(targetUser.getUsername());
        kickVO.setOperatorId(operatorId);
        kickVO.setOperatorName(operator.getUsername());
        kickVO.setChatType(MessageChatType.GROUP_KICK);
        broadcastGroupMessageToMembers(kickVO, groupId);
        pushToUserPrivateChannel(targetUserId, kickVO);
        log.info("用户 {} 将 {} 移出群 {}", operatorId, targetUserId, groupId);
    }

    private void dissolveGroup(String groupId, Long ownerUserId) {
        entityManager.flush();
        entityManager.clear();
        entityManager.createNativeQuery("DELETE FROM chat_group_administrators WHERE group_id = ?1")
                .setParameter(1, groupId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM chat_group_members WHERE group_id = ?1")
                .setParameter(1, groupId)
                .executeUpdate();
        userChatSessionService.removeAllGroupSessions(groupId);
        chatGroupRepository.deleteById(groupId);
        log.info("群主 {} 解散群 {}", ownerUserId, groupId);
    }

    private void removeMemberFromGroup(String groupId, Long userId) {
        ChatGroupMemberProjection memberProj = chatGroupMemberRepository
                .findByMemberUserIdAndChatGroupGroupId(userId, groupId)
                .orElseThrow(() -> new BusinessException(403, "用户不是群成员"));

        ChatGroupMemberId memberId = new ChatGroupMemberId(
                memberProj.getMemberId(),
                memberProj.getGroupId()
        );
        chatGroupMemberRepository.deleteById(memberId);
        userChatSessionService.removeGroupSession(userId, groupId);
        joinRequestRepo.deleteByUserUserIdAndGroupGroupId(userId, groupId);
        chatGroupAdminRepository.findByChatGroupGroupIdAndUserUserId(groupId, userId)
                .ifPresent(chatGroupAdminRepository::delete);
        groupRoomUtils.leaveGroupRoom(groupId, userId);
    }

    public void RefuseJoinRequest(dealJoinGroupRoomRequest dealJoinGroupRoomRequest) {
        Long processorId = BaseContext.getCurrentId();
        Long requestId = dealJoinGroupRoomRequest.getRequestId();
        // 1. 查询申请记录
        GroupJoinRequest request = joinRequestRepo.findById(requestId)
                .orElseThrow(() -> new BusinessException(403, "申请不存在"));

        // 2. 验证处理人是否是群主
        ChatGroup group = request.getGroup();
        if (!isUserGroupAdmin(group.getGroupId(), processorId)) {
            if (!group.getCreator().getUserId().equals(processorId)) {
                throw new BusinessException(403, "您不是管理员，无权处理此申请");
            }
        }
        // 3. 更新申请状态
        request.setStatus(RequestStatus.REJECTED);
        request.setProcessTime(LocalDateTime.now());
        request.setProcessor(
                userInfoRepository.findById(processorId).orElseThrow(() -> new BusinessException(403, "处理人不存在")));
        joinRequestRepo.save(request);
        pushToUserPrivateChannel(request.getUser().getUserId(),
                new JoinGroupRoomVO(request.getUser().getUserId(), group.getGroupId(),
                        group.getGroupName(), group.getGroupAvatar(), JoinGroupStatus.REFUSE,
                        MessageChatType.GROUP_REFUSE_REQUESTS));
    }

    /**
     * 群主处理加群申请
     */
    @Transactional
    public void agreeJoinRequest(dealJoinGroupRoomRequest joinGroupRoomRequest) {
        Long processorId = BaseContext.getCurrentId();
        Long requestId = joinGroupRoomRequest.getRequestId();
        // 1. 查询申请记录
        GroupJoinRequest request = joinRequestRepo.findById(requestId)
                .orElseThrow(() -> new BusinessException(403, "申请不存在"));

        // 2. 验证处理人是否是群主
        ChatGroup group = request.getGroup();
        if (!isUserGroupAdmin(group.getGroupId(), processorId)) {
            if (!group.getCreator().getUserId().equals(processorId)) {
                throw new BusinessException(403, "您不是管理员，无权处理此申请");
            }
        }
        // 3. 更新申请状态
        request.setStatus(RequestStatus.ACCEPTED);
        request.setProcessTime(LocalDateTime.now());
        request.setProcessor(
                userInfoRepository.findById(processorId).orElseThrow(() -> new BusinessException(403, "处理人不存在")));
        joinRequestRepo.save(request);

        // 4. 将用户加入群成员表
        ChatGroupMember member = ChatGroupMember.builder()
                .id(new ChatGroupMemberId(request.getUser().getUserId(), group.getGroupId()))
                .member(request.getUser())
                .chatGroup(group)
                .joinTime(LocalDateTime.now())
                .build();

        chatGroupMemberRepository.save(member);
        groupRoomUtils.addUserToGroup(group.getGroupId(), request.getUser().getUserId());

        pushToUserPrivateChannel(request.getUser().getUserId(),
                new JoinGroupRoomVO(request.getUser().getUserId(), group.getGroupId(),
                        group.getGroupName(), group.getGroupAvatar(), JoinGroupStatus.AGREE,
                        MessageChatType.GROUP_AGREE_REQUESTS));
    }

    public updateGroupAvatarVO uploadGroupAvatar(MultipartFile avatarFile, String groupId) {
        Long userId = BaseContext.getCurrentId();
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(403, "群不存在"));
        if (!isUserGroupAdmin(group.getGroupId(), userId)) {
            if (!group.getCreator().getUserId().equals(userId)) {
                throw new BusinessException(403, "您不是管理员，无法上传群头像");
            }
        }
        String oldAvatarUrl = group.getGroupAvatar();
        CosUtil.UploadedImage uploaded = cosUtil.uploadGroupAvatar(avatarFile, groupId);
        ImageAuditVerdict verdict = ciImageAuditService.auditAvatar(uploaded.objectKey(), uploaded.sizeBytes());
        if (verdict != ImageAuditVerdict.PASS) {
            cosUtil.deleteByUrl(uploaded.url());
            throw new BusinessException(400, verdict == ImageAuditVerdict.REJECT
                    ? "群头像含有违规内容，请更换图片"
                    : "群头像未通过安全审核，请更换图片");
        }
        String avatarUrl = uploaded.url();
        if (StringUtils.isNotBlank(oldAvatarUrl)) {
            cosUtil.deleteByUrl(oldAvatarUrl);
            log.info("群头像删除成功 → 群ID: {}, 旧URL: {}", groupId, oldAvatarUrl);
        }
        chatGroupRepository.updateGroupAvatar(groupId, avatarUrl);
        userChatSessionService.updateGroupAvatar(groupId, avatarUrl);
        updateGroupAvatarVO updateGroupAvatarVO = new updateGroupAvatarVO(groupId, avatarUrl,
                MessageChatType.GROUP_UPDATE_AVATAR);
        broadcastGroupMessageToMembers(updateGroupAvatarVO, groupId);
        return updateGroupAvatarVO;
    }

    private void pushToUserPrivateChannel(Long userId, Object payload) {
        if (userId == null) return;
        stompUserPushService.pushPrivateChannel(userId, payload);
    }

    /**
     * 向群成员广播消息（给每个成员发私信）
     *
     * @param messageVo 要发送的消息VO
     * @param groupId   群聊ID
     */
    private void broadcastGroupMessageToMembers(Object messageVo, String groupId) {
        broadcastGroupMessageToMembers(messageVo, groupId, null);
    }

    private void broadcastGroupMessageToMembers(Object messageVo, String groupId, Long excludeUserId) {
        List<Long> memberIds = chatGroupMemberRepository.findMemberUserIdsByGroupId(groupId);
        if (CollectionUtils.isEmpty(memberIds)) {
            memberIds = new ArrayList<>(groupRoomUtils.getGroupMembers(groupId));
        } else {
            groupRoomUtils.replaceMembers(groupId, memberIds);
        }
        List<Long> targets = memberIds.stream()
                .filter(Objects::nonNull)
                .filter(memberId -> excludeUserId == null || !memberId.equals(excludeUserId))
                .toList();
        log.info("群聊消息广播: groupId={}, targets={}", groupId, targets);
        targets.forEach(memberId -> pushToUserPrivateChannel(memberId, messageVo));
    }

    public updateGroupNameVO updateGroupName(updateGroupNameDTO request) {
        Long userId = BaseContext.getCurrentId();
        ChatGroup group = chatGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new BusinessException(403, "群不存在"));
        if (!isUserGroupAdmin(group.getGroupId(), userId)) {
            if (!group.getCreator().getUserId().equals(userId)) {
                throw new BusinessException(403, "您不是群主或管理员，无权修改群名称");
            }
        }
        String groupName = request.getGroupName();
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new BusinessException(400, "群名称不能为空");
        }
        groupName = groupName.trim();
        if (groupName.length() > 32) {
            throw new BusinessException(400, "群名称最多32个字");
        }
        chatGroupRepository.updateGroupName(request.getGroupId(), groupName);
        userChatSessionService.updateGroupName(request.getGroupId(), groupName);
        updateGroupNameVO updateGroupNameVO = new updateGroupNameVO();
        updateGroupNameVO.setGroupId(request.getGroupId());
        updateGroupNameVO.setGroupName(groupName);
        updateGroupNameVO.setChatType(MessageChatType.GROUP_UPDATE_NAME);
        broadcastGroupMessageToMembers(updateGroupNameVO, request.getGroupId());
        return updateGroupNameVO;
    }


    /*
     * 获取群成员列表
     */
    public AllChatGroupMemberListVO getAllGroupUsers(String groupId) {
        List<ChatGroupMemberWithRole> membersWithRole = chatGroupMemberRepository.findAllMembersWithRole(groupId);
        // 2. 转换为前端VO：映射成员基础信息 + 角色身份
        Integer totalCount = membersWithRole.size();
        List<ChatGroupMemberVO> memberVos = membersWithRole.stream()
                .map(this::convertToMemberVOWithRole) // 转换每个成员
                .collect(Collectors.toList());

        // 3. 构建完整分页结果（含群ID、成员列表、分页元数据）
        return new AllChatGroupMemberListVO(groupId,memberVos,totalCount);
    }


    /*
     * 获取群成员列表
     */
    public ChatGroupMemberListVO getGroupUsers(String groupId, Pageable pageable) {
        // 1. 分页查询群成员及角色信息（左连接管理员表）
        Page<ChatGroupMemberWithRole> memberWithRolePage = chatGroupMemberRepository.findMembersWithRole(groupId,
                pageable);

        // 2. 转换为前端VO：映射成员基础信息 + 角色身份
        List<ChatGroupMemberVO> memberVos = memberWithRolePage.getContent().stream()
                .map(this::convertToMemberVOWithRole) // 转换每个成员
                .collect(Collectors.toList());

        // 3. 构建完整分页结果（含群ID、成员列表、分页元数据）
        return buildGroupMemberListVO(groupId, memberWithRolePage, memberVos);
    }

    /**
     * 将"成员+角色"投影转换为VO
     */
    private ChatGroupMemberVO convertToMemberVOWithRole(ChatGroupMemberWithRole withRole) {
        ChatGroupMember member = withRole.getMember();
        ChatGroupAdministrator admin = withRole.getAdministrator();
        UserInfo userInfo = member.getMember();

        // 根据管理员记录判断角色
        GroupRoleEnum groupRole = determineGroupRole(admin);

        return new ChatGroupMemberVO(
                userInfo.getUserId(), // 成员ID
                userInfo.getUsername(), // 成员昵称
                userInfo.getAvatar(), // 成员头像
                groupRole // 群角色（枚举类型）
        );
    }

    /**
     * 根据管理员记录确定群角色
     */
    private GroupRoleEnum determineGroupRole(ChatGroupAdministrator admin) {
        if (admin != null) {
            return admin.getIsOwner() ? GroupRoleEnum.GROUP_OWNER : GroupRoleEnum.GROUP_ADMIN;
        }
        return GroupRoleEnum.GROUP_MEMBER; // 无管理员记录 → 普通成员
    }

    /**
     * 构建分页结果VO（补充总页数等元数据）
     */
    private ChatGroupMemberListVO buildGroupMemberListVO(
            String groupId,
            Page<ChatGroupMemberWithRole> memberPage,
            List<ChatGroupMemberVO> memberVos) {

        return new ChatGroupMemberListVO(
                groupId, // 群ID
                memberVos, // 成员VO列表（含角色）
                memberPage.getTotalElements(), // 总成员数
                memberPage.getNumber() + 1, // 当前页码（Spring Data从0→前端1）
                memberPage.getSize(), // 每页大小
                (int) Math.ceil((double) memberPage.getTotalElements() / memberPage.getSize()) // 总页数
        );
    }

    /**
     * 获取群入群申请分页列表（带权限校验：仅管理员可查）
     * 
     * @param pageable 分页参数
     * @return 分页的VO结果
     */
    public ChatGroupJoinRequestListVO getGroupJoinRequests(Pageable pageable) {
        Long currentUserId = BaseContext.getCurrentId();
        Page<GroupJoinRequest> requestPage = joinRequestRepo.findByManagedGroupsOrderByApplyTimeDesc(currentUserId,
                pageable);

        // 转换为VO
        List<GroupJoinRequestVO> voList = requestPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 构造分页结果
        return ChatGroupJoinRequestListVO.builder()
                .totalPages(requestPage.getTotalPages())
                .currentPage(requestPage.getNumber() + 1) // 适配前端页码（从1开始）
                .pageSize(requestPage.getSize())
                .data(voList)
                .chatType(MessageChatType.GROUP_JOIN_REQUESTS)
                .build();
    }

    /**
     * 将GroupJoinRequest实体转换为VO
     */
    private GroupJoinRequestVO convertToVO(GroupJoinRequest request) {
        return GroupJoinRequestVO.builder()
                .requestId(request.getId())
                .applicantName(request.getUser().getUsername()) // 申请人昵称
                .groupName(request.getGroup().getGroupName()) // 群名称
                .description(request.getDescription()) // 加群描述
                .status(request.getStatus().getDescription()) // 状态中文描述（需枚举支持）
                .applyTime(request.getApplyTime()) // 申请时间
                .processTime(request.getProcessTime()) // 处理时间
                .processorName(request.getProcessor() != null ? request.getProcessor().getUsername() : null) // 处理人昵称
                .build();
    }

    @Transactional(rollbackFor = Exception.class) // 异常回滚
    public void addAdministrator(String groupId, Long newAdminUserId) {
        Long currentUserId = BaseContext.getCurrentId();
        // 1. 校验群存在
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(403, "群不存在"));

        // 2. 校验被加用户存在
        UserInfo newAdmin = userInfoRepository.findById(newAdminUserId)
                .orElseThrow(() -> new BusinessException(403, "被添加用户不存在"));

        // 3. 校验被加用户是群成员（必须先加入群才能当管理员）
        boolean isMember = chatGroupMemberRepository.existsById_MemberIdAndId_GroupId(newAdminUserId, groupId);
        if (!isMember) {
            throw new BusinessException(403, "用户未加入群，无法添加为管理员");
        }

        // 4. 校验当前用户是群主（只有群主能加管理员）
        boolean isCurrentOwner = chatGroupAdminRepository.existsById_GroupIdAndId_UserIdAndIsOwnerTrue(groupId,
                currentUserId);
        if (!isCurrentOwner) {
            throw new BusinessException(403, "只有群主可以添加管理员");
        }

        // 5. 校验被加用户不是已有管理员（避免重复添加）
        boolean exists = chatGroupAdminRepository.existsById_GroupIdAndId_UserId(groupId, newAdminUserId);
        if (exists) {
            throw new BusinessException(403, "用户已经是管理员");
        }

        // 6. 创建并保存管理员记录（isOwner=0：普通管理员）
        ChatGroupAdministrator admin = new ChatGroupAdministrator();
        admin.setId(new ChatGroupAdministratorId()); // 关键：初始化复合主键对象
        admin.setChatGroup(group); // 关联群实体（自动填充复合主键的groupId）
        admin.setUser(newAdmin); // 关联用户实体（自动填充复合主键的userId）
        admin.setIsOwner(false); // 普通管理员
        chatGroupAdminRepository.save(admin);
    }

    // ===================== 删除普通管理员 =====================
    @Transactional(rollbackFor = Exception.class)
    public void removeAdministrator(String groupId, Long adminUserId) {
        Long currentUserId = BaseContext.getCurrentId();
        // 1. 校验要删除的管理员记录存在
        ChatGroupAdministrator admin = chatGroupAdminRepository.findById_GroupIdAndId_UserId(groupId, adminUserId)
                .orElseThrow(() -> new BusinessException(403, "管理员不存在"));

        // 2. 校验当前用户是群主
        boolean isCurrentOwner = chatGroupAdminRepository.existsById_GroupIdAndId_UserIdAndIsOwnerTrue(groupId,
                currentUserId);
        if (!isCurrentOwner) {
            throw new BusinessException(403, "只有群主可以删除管理员");
        }

        // 3. 校验不能删除群主自己（isOwner=1表示群主）
        if (admin.getIsOwner()) {
            throw new BusinessException(403, "不能删除群主");
        }

        // 4. 删除管理员记录
        chatGroupAdminRepository.delete(admin);
    }

    // 验证当前用户是群的「管理员」（含群主）
    public boolean isUserGroupAdmin(String groupId, Long userId) {
        return chatGroupAdminRepository.existsById_GroupIdAndId_UserId(groupId, userId);
    }

    // 验证当前用户是群的「群主」（仅群主可操作）
    public boolean isUserGroupOwner(String groupId, Long userId) {
        return chatGroupAdminRepository.existsById_GroupIdAndId_UserIdAndIsOwnerTrue(groupId, userId);
    }

    // 强制校验：不是管理员则抛异常（通用）
    public void validateGroupAdmin(String groupId, Long userId) {
        if (!isUserGroupAdmin(groupId, userId)) {
            throw new BusinessException(403, "无管理员权限，操作拒绝");
        }
    }

    // 强制校验：不是群主则抛异常（通用）
    public void validateGroupOwner(String groupId, Long userId) {
        if (!isUserGroupOwner(groupId, userId)) {
            throw new BusinessException(403, "仅群主可操作");
        }
    }

    public GroupDetailVO getGroupDetail(String groupId) {
        Long currentUserId = BaseContext.getCurrentId();
        // 查询群基本信息
        ChatGroup group = chatGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException(403, "群不存在"));
        // 验证当前用户是否是群成员（非成员无法看群详情？根据业务调整）
        chatGroupMemberRepository.existsByChatGroupGroupIdAndMemberUserId(groupId, currentUserId);

        // 构造返回的权限状态
        GroupDetailVO vo = new GroupDetailVO();
        vo.setGroupId(groupId);
        if (isUserGroupOwner(groupId, currentUserId)) {
            vo.setGroupRoleEnum(GroupRoleEnum.GROUP_OWNER);
            return vo;
        }
        // 当前用户是否是管理员（含群主）
        if (isUserGroupAdmin(groupId, currentUserId)) {
            vo.setGroupRoleEnum(GroupRoleEnum.GROUP_ADMIN);
            return vo;
        }
        vo.setGroupRoleEnum(GroupRoleEnum.GROUP_MEMBER);
        return vo;
    }

    /**
     * 核心：查询当前用户已发送的群申请（分页）
     * 
     * @param pageable 分页参数
     * @return 分页的VO结果
     */
    public MineChatGroupJoinRequestListVO getGroupPermission(Pageable pageable) {
        Long currentUserId = BaseContext.getCurrentId();
        // 1. 查询当前用户的申请（分页+按时间倒序）
        Page<GroupJoinRequest> requestPage = joinRequestRepo.findByUser_UserIdOrderByApplyTimeDesc(currentUserId,
                pageable);

        // 2. 转换为VO列表
        List<GroupPermissionVO> voList = requestPage.getContent().stream()
                .map(GroupPermissionVO::convertToGroupPermissionVO)
                .collect(Collectors.toList());

        // 3. 构造分页结果
        return MineChatGroupJoinRequestListVO.builder()
                .totalPages(requestPage.getTotalPages()) // 总页数
                .currentPage(requestPage.getNumber() + 1) // 当前页码（适配前端从1开始）
                .pageSize(requestPage.getSize()) // 每页数量
                .data(voList) // 申请数据
                .build();
    }

    /**
     * 撤回加群申请（根据申请ID，更安全）
     * @param requestId 加群申请ID
     * @param userId    当前登录用户ID（校验权限）
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupApplyRetractNotificationVO retractGroupJoinApplyById(Long requestId, Long userId) {
        GroupJoinRequest request = joinRequestRepo
                .findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在或无权限"));

        // 校验申请状态：仅待处理的申请可撤回
        if (!request.getStatus().equals(RequestStatus.PENDING)) {
            throw new BusinessException(400, "申请已处理，无法撤回");
        }

        request.setStatus(RequestStatus.RETRACTED);
        joinRequestRepo.save(request);

        // 发送通知
        return sendGroupApplyRetractNotification(request);
    }

    /**
     * 发送加群撤回通知给申请人（WebSocket）
     */
    private GroupApplyRetractNotificationVO sendGroupApplyRetractNotification(GroupJoinRequest request) {
        GroupApplyRetractNotificationVO notification = new GroupApplyRetractNotificationVO();
        notification.setStatus(RequestStatus.RETRACTED); // 消息类型：加群撤回
        notification.setRequestId(request.getId()); // 申请ID
        notification.setRetractTime(LocalDateTime.now());
        notification.setChatType(MessageChatType.GROUP_RETRACT_REQUESTS);

        // 发送给申请人的私人频道（前端需订阅：/user/{userId}/queue/private-messages）
        pushToUserPrivateChannel(request.getUser().getUserId(), notification);
        return notification;
    }



    public GroupIdentitiesVO getGroupIdentities(String groupId) {
        List<ChatGroupAdministrator> allAdmins = chatGroupAdminRepository.findByChatGroupGroupId(groupId);
        if (CollectionUtils.isEmpty(allAdmins)) {
            GroupIdentitiesVO vo = new GroupIdentitiesVO();
            vo.setGroupId(groupId);
            chatGroupRepository.findById(groupId).ifPresent(group -> {
                if (group.getCreator() != null) {
                    vo.setOwnerId(group.getCreator().getUserId());
                }
            });
            vo.setAdmins(Collections.emptyList());
            return vo;
        }
        return getGroupIdentitiesVO(allAdmins, groupId);
    }

    @NotNull
    private static GroupIdentitiesVO getGroupIdentitiesVO(List<ChatGroupAdministrator> allAdmins, String groupId) {
        Long ownerId = null;
        List<Long> adminIds = new ArrayList<>();

        for (ChatGroupAdministrator admin : allAdmins) {
            if (Boolean.TRUE.equals(admin.getIsOwner())) {
                // 群主（假设群主唯一，取第一个匹配的）
                ownerId = admin.getUser().getUserId();
            } else {
                // 普通管理员
                adminIds.add(admin.getUser().getUserId());
            }
        }

        // 3. 转换为VO（若群主不存在，ownerId为null）
        GroupIdentitiesVO vo = new GroupIdentitiesVO();
        vo.setGroupId(groupId);
        vo.setOwnerId(ownerId);
        vo.setAdmins(adminIds);
        return vo;
    }

    public GroupAdminDetailsVO getAdminDetails(String groupId) {
        List<ChatGroupAdministrator> allAdmins = chatGroupAdminRepository.findByChatGroupGroupId(groupId);
        if (CollectionUtils.isEmpty(allAdmins)) {
            // 无管理员记录，返回空VO
            GroupAdminDetailsVO vo = new GroupAdminDetailsVO();
            vo.setGroupId(groupId);
            vo.setAdmins(Collections.emptyList());
            return vo;
        }

        // 2. 过滤分组：群主（isOwner=true）和管理员（isOwner=false）
        return getGroupAdminDetailsVO(allAdmins,groupId);
    }

    @NotNull
    private static GroupAdminDetailsVO getGroupAdminDetailsVO(List<ChatGroupAdministrator> allAdmins, String groupId) {
        Long ownerId = null;
        List<AdminDetailsVO> admins = new ArrayList<>(); // 显式初始化

        for (ChatGroupAdministrator admin : allAdmins) {
            if (Boolean.TRUE.equals(admin.getIsOwner())) {
                // 群主（假设群主唯一，取第一个匹配的）
                ownerId = admin.getUser().getUserId();
            } else {
                // 普通管理员
                admins.add(new AdminDetailsVO(admin.getId().getUserId(), admin.getUser().getUsername(), admin.getUser().getAvatar()));
            }
        }

        // 3. 转换为VO（若群主不存在，ownerId为null）
        GroupAdminDetailsVO vo = new GroupAdminDetailsVO();
        vo.setGroupId(groupId);
        vo.setAdmins(admins);
        return vo;
    }
}
