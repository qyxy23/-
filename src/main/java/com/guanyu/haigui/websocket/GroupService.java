package com.guanyu.haigui.websocket;


import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Enum.MessageStatus;
import com.guanyu.haigui.Enum.RequestStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.vo.ChatGroupVo;
import com.guanyu.haigui.pojo.vo.GroupMessageVO;
import com.guanyu.haigui.pojo.vo.GroupRoomListVO;
import com.guanyu.haigui.repository.*;
import com.guanyu.haigui.utils.RedisServiceUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final GroupJoinRequestRepository joinRequestRepo;
    private final UserInfoRepository userInfoRepository;
    @PersistenceContext
    private EntityManager entityManager;
    private final RedisServiceUtil redisServiceUtil;
    // private static final int LATEST_MESSAGES_LIMIT = 20;


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
            throw new BusinessException("您已提交过加群申请，请等待处理");
        }

        // 2. 验证群是否存在
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException("群不存在"));

        // 3. 创建加群申请记录
        GroupJoinRequest joinRequest = new GroupJoinRequest();
        joinRequest.setUser(userInfoRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在")));
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
        notification.setGroupId(group.getGroupId());
        notification.setDescription(request.getDescription());

        // 发送给群主（群主订阅此主题：/topic/group/{groupId}/join-requests）
        simpMessagingTemplate.convertAndSend("/topic/group/" + group.getGroupId() + "/join-requests", notification);
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

        // 4. 添加群成员（创建者+好友）
        addUserToGroup(newGroup.getGroupId(), currentUserId); // 添加创建者
        request.getFriendIds().forEach(friendId -> addUserToGroup(newGroup.getGroupId(), friendId)); // 添加好友

        return newGroup.getGroupId();
    }

    private void validateFriendRelations(Long userId, List<Long> friendIds) {
        if (CollectionUtils.isEmpty(friendIds)) {
            throw new BusinessException("好友列表不能为空");
        }

        // 查询「所有与当前用户相关的好友关系」（包括主动添加和被添加）
        List<FriendRelation> relations = friendRelationsRepository
                .findByUserUserIdOrFriendUserId(userId, userId); // 获取当前用户参与的所有关系

        // 提取有效好友ID：当前用户与friendIds中的用户是「双向ACCEPTED」关系
        Set<Long> validFriendIds = new HashSet<>();

        // 场景1：当前用户是「发起方」（user_user_id=userId），好友是friend_user_id
        List<Long> initiatedFriendIds = relations.stream()
                .filter(rel ->
                        rel.getUser().getUserId().equals(userId) && // 当前用户是发起方
                                rel.getStatus() == FriendStatus.ACCEPTED && // 关系已接受
                                friendIds.contains(rel.getFriend().getUserId()) // 好友在请求列表中
                )
                .map(rel -> rel.getFriend().getUserId())
                .toList();

        // 场景2：当前用户是「被添加方」（friend_user_id=userId），好友是user_user_id
        List<Long> receivedFriendIds = relations.stream()
                .filter(rel ->
                        rel.getFriend().getUserId().equals(userId) && // 当前用户是被添加方
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
            throw new BusinessException("以下用户不是您的好友：" + invalidIds);
        }
    }

    /**
     * 构建群聊实体（默认值：头像空、名称可自动生成）
     */
    private ChatGroup buildChatGroup(Long creatorId) {
        return ChatGroup.builder()
                .groupId(generateGroupId()) // 生成UUID群ID
                .groupName("群聊-" + generateShortUuid()) // 可选：自动生成群名称（如不需要可注释）
                .creator(sysUserRepository.findById(creatorId).orElseThrow(() -> new BusinessException("创建者不存在")))
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

        ChatGroupMemberId memberIdObj = new ChatGroupMemberId(memberId, groupId);
        ChatGroupMember member = ChatGroupMember.builder()
                .id(memberIdObj)
                .member(sysUserRepository.findById(memberId).orElseThrow(() -> new BusinessException("用户不存在")))
                .chatGroup(chatGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException("群聊不存在")))
                .joinTime(LocalDateTime.now())
                .build();

        chatGroupMemberRepository.save(member);
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
        Sort.Direction direction = Sort.Direction.fromString(sortOrder);
        // 用前端传的 sortField 转换为实体属性名（比如 memberCount → members）
        String sortFieldEntity = getOrderField(sortField);
        // 构造 Spring Data 的 Sort
        Sort sort = Sort.by(direction, sortFieldEntity);
        // 构造 Pageable
        Pageable pageable = PageRequest.of(page, pageSize, sort);
        return new PageImpl<>(voList, pageable, total);
    }

    /**
     * 构建动态排序条件
     */
    private Order buildOrder(CriteriaBuilder cb, Root<ChatGroup> cg, Join<ChatGroup, ChatGroupMember> cgm,
                             String sortField, String sortOrder) {
        Sort.Direction direction = Sort.Direction.fromString(sortOrder);
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
                .groupName(group.getGroupName())
                .creatorName(group.getCreator().getUsername())
                .memberCount(memberCount)
                .createTime(group.getCreateTime())
                .build();
    }

    /**
     * 获取指定群聊的历史消息（仅分页，无排序）
     * @param dto 查询参数（群ID、分页）
     * @return 分页后的群消息VO列表
     */
    public Page<GroupMessageVO> getGroupMessages(GroupChatHistoryDTO dto) {
        // 内联构建查询条件：过滤群ID + 预加载关联实体
        Specification<GroupMessage> spec = (root, query, cb) -> {
            // 过滤条件：消息所属群ID等于目标群
            return cb.equal(root.get("chatGroup").get("groupId"), dto.getGroupId());
        };
        redisServiceUtil.clearGroupMsgCount(BaseContext.getCurrentId(),dto.getGroupId());

        // 构建分页请求（无排序）
        Pageable pageable = PageRequest.of(dto.getPage(), dto.getSize());
        // 执行查询（返回GroupMessage分页）
        Page<GroupMessage> messagePage = chatGroupMessageRepository.findAll(spec, pageable);
        // 转换为GroupMessageVO分页
        return messagePage.map(GroupMessageVO::from);
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
            throw new BusinessException("您不是群成员，无法发送消息");
        }

        // 2. 获取群和发送者实体（避免懒加载异常）
        ChatGroup group = chatGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new BusinessException("群不存在"));
        UserInfo sender = userInfoRepository.findById(userID)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 3. 创建并保存消息
        GroupMessage message = new GroupMessage();
        message.setChatGroup(group);
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setMessageType(request.getMessageType());
        message.setCreateTime(LocalDateTime.now());
        message.setStatus(MessageStatus.SENT);
        chatGroupMessageRepository.save(message);
        redisServiceUtil.updateLastGroupMessage1(request.getGroupId(),message.getContent(),message.getCreateTime());
        redisServiceUtil.updateLastGroupSenderId(request.getGroupId(),message.getSender().getUserId());
        // 4. 转换为VO并广播给群成员
        GroupMessageVO vo = GroupMessageVO.from(message);
        simpMessagingTemplate.convertAndSend(
                "/topic/group/" + request.getGroupId() + "/messages", // 群消息主题
                vo
        );
        return vo;
    }

    public void leaveGroupRoom(String groupId) {
        Long userId = BaseContext.getCurrentId();
        // 1. 查询群成员记录
        Optional<ChatGroupMember> memberOpt = chatGroupMemberRepository
                .findByMemberUserIdAndChatGroupGroupId(userId, groupId);

        if (memberOpt.isPresent()) {
            // 2. 删除群成员记录
            chatGroupMemberRepository.delete(memberOpt.get());

            // 3. 广播用户退出事件（客户端可更新群成员列表）
            simpMessagingTemplate.convertAndSend(
                    "/topic/group/" + groupId + "/exit", // 退出主题
                    userId // 退出的用户ID
            );
        } else {
            throw new BusinessException("您未加入该群，无法退出");
        }
    }

    public void RefuseJoinRequest(dealJoinGroupRoomRequest dealJoinGroupRoomRequest) {
        Long processorId = BaseContext.getCurrentId();
        Long requestId = dealJoinGroupRoomRequest.getRequestId();
        // 1. 查询申请记录
        GroupJoinRequest request = joinRequestRepo.findById(requestId)
                .orElseThrow(() -> new BusinessException("申请不存在"));

        // 2. 验证处理人是否是群主
        ChatGroup group = request.getGroup();
        if (!group.getCreator().getUserId().equals(processorId)) {
            throw new BusinessException("您不是群主，无权处理此申请");
        }

        // 3. 更新申请状态
        request.setStatus(RequestStatus.REJECTED);
        request.setProcessTime(LocalDateTime.now());
        request.setProcessor(userInfoRepository.findById(processorId).orElseThrow(() -> new BusinessException("处理人不存在")));
        joinRequestRepo.save(request);
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
                .orElseThrow(() -> new BusinessException("申请不存在"));

        // 2. 验证处理人是否是群主
        ChatGroup group = request.getGroup();
        if (!group.getCreator().getUserId().equals(processorId)) {
            throw new BusinessException("您不是群主，无权处理此申请");
        }

        // 3. 更新申请状态
        request.setStatus(RequestStatus.ACCEPTED);
        request.setProcessTime(LocalDateTime.now());
        request.setProcessor(userInfoRepository.findById(processorId).orElseThrow(() -> new BusinessException("处理人不存在")));
        joinRequestRepo.save(request);

        // 4. 将用户加入群成员表
        ChatGroupMember member = ChatGroupMember.builder()
                .id(new ChatGroupMemberId(request.getUser().getUserId(), group.getGroupId()))
                .member(request.getUser())
                .chatGroup(group)
                .joinTime(LocalDateTime.now())
                .build();

        chatGroupMemberRepository.save(member);

        // 5. （可选）通知申请人入群成功
        // sendJoinSuccessToApplicant(request.getUser());
    }
}
