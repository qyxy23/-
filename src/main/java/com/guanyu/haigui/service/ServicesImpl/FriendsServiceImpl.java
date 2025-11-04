package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.Assert;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.Exception.FriendsException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMsgDTO;
import com.guanyu.haigui.pojo.model.FriendRelation;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.repository.FriendRelationRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.FriendsService;
import com.guanyu.haigui.utils.RedisServiceUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
@Service
public class FriendsServiceImpl implements FriendsService {

    private final UserInfoRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final RedisServiceUtil redisServiceUtil;

    /** 获取当前用户收到的好友申请列表（待处理） */
    public List<FriendApplicationVO> getReceivedApplications() {
        Long currentUserId = BaseContext.getCurrentId();
        // 只查询PENDING状态的申请
        List<FriendStatus> statuses = Collections.singletonList(FriendStatus.PENDING);
        List<FriendRelation> relations = friendRelationRepository.findReceivedApplications(currentUserId, statuses);
        return relations.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /** 获取当前用户发送的好友申请列表（待处理） */
    public List<FriendApplicationVO> getSentApplications() {
        Long currentUserId = BaseContext.getCurrentId();
        List<FriendStatus> statuses = Collections.singletonList(FriendStatus.PENDING);
        List<FriendRelation> relations = friendRelationRepository.findSentApplications(currentUserId, statuses);
        return relations.stream().map(this::convertToVO2).collect(Collectors.toList());
    }

    /** 将FriendRelation实体转换为VO */
    private FriendApplicationVO convertToVO(FriendRelation relation) {
        FriendApplicationVO vo = new FriendApplicationVO();
        vo.setApplicationId(relation.getId());
        vo.setRemark(relation.getRemark());
        vo.setStatus(relation.getStatus());
        vo.setCreateTime(relation.getApplyTime());

        // 查询申请人（主动方）的信息（sys_user表）
        userRepository.findById(relation.getUser().getUserId())
                .ifPresentOrElse(applicant -> {
                    vo.setApplicantId(applicant.getUserId());
                    vo.setApplicantName(applicant.getUsername());
                    vo.setApplicantAvatar(applicant.getAvatar());
                }, () -> log.warn("申请人不存在：{}", relation.getFriend().getUsername()));

        return vo;
    }

    /** 将FriendRelation实体转换为VO（查询被申请方信息） */
    private FriendApplicationVO convertToVO2(FriendRelation relation) {
        FriendApplicationVO vo = new FriendApplicationVO();
        vo.setApplicationId(relation.getId());
        vo.setRemark(relation.getRemark());
        vo.setStatus(relation.getStatus());
        vo.setCreateTime(relation.getApplyTime());

        // 关键修正：查询「被申请方」（friend_id对应的用户）的信息！！！
        userRepository.findById(relation.getFriend().getUserId()) // 不是getUserId()！
                .ifPresentOrElse(targetUser -> {
                    vo.setApplicantId(targetUser.getUserId()); // 被申请方ID
                    vo.setApplicantName(targetUser.getUsername()); // 被申请方昵称
                    vo.setApplicantAvatar(targetUser.getAvatar()); // 被申请方头像
                }, () -> log.warn("被申请方不存在：{}", relation.getFriend().getUsername()));

        return vo;
    }


    /**
     * 搜索好友（分页+过滤）
     * @param keyword 搜索关键字
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<FriendSearchResultVO> searchFriends(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new FriendsException("搜索关键字不能为空");
        }
        Long currentUserId = BaseContext.getCurrentId();
        // 直接调用分页查询方法（已包含所有过滤条件）
        return userRepository.searchPotentialFriendsWithFilters(keyword, currentUserId, pageable);
    }


    /**
     * 获取好友列表（含未读消息数、最后一条消息）
     * @return 好友完整信息列表
     */
    public List<FriendSearchListVO> getFriendListWithMessages() {
        long currentUserId = BaseContext.getCurrentId();
        // 1. 查询好友基础信息（仅昵称、头像、ID）
        List<FriendBasicInfoVO> basicInfos = userRepository.findFriendBasicInfos(currentUserId);
        if (CollectionUtils.isEmpty(basicInfos)) return Collections.emptyList();

        // 提取好友ID列表（批量操作）
        List<Long> friendIds = basicInfos.stream()
                .map(FriendBasicInfoVO::getUserId)
                .toList();

        // 2. 优先从Redis查询未读数和最后一条消息
        Map<Long, Long> unreadMap = new HashMap<>();       // 好友ID → 未读数
        Map<Long, PrivateMsgDTO> lastMsgMap = new HashMap<>();// 好友ID → 最后一条消息

        // 批量查Redis（避免循环单查）
        for (Long friendId : friendIds) {
            // 查未读数
            Long unreadCount = redisServiceUtil.selectUnreadMsgCountFromRedis(currentUserId, friendId);
            if (unreadCount != null) unreadMap.put(friendId, unreadCount);

            // 查最后一条消息
            PrivateMsgDTO lastMsg = redisServiceUtil.selectLastMessageFromRedis(currentUserId, friendId);
            if (lastMsg != null) lastMsgMap.put(friendId, lastMsg);
        }

        // 3. 收集Redis中缺失的好友ID（需要查数据库）
        List<Long> missingUnreadIds = friendIds.stream()
                .filter(id -> !unreadMap.containsKey(id))
                .collect(Collectors.toList());
        List<Long> missingLastMsgIds = friendIds.stream()
                .filter(id -> !lastMsgMap.containsKey(id))
                .collect(Collectors.toList());

        // 4. 批量查数据库（未读数）
        Map<Long, Long> unreadFromDb = new HashMap<>();
        if (!missingUnreadIds.isEmpty()) {
            List<Object[]> unreadResults = userRepository.countUnreadMessagesByFriendIds(currentUserId, missingUnreadIds);
            unreadFromDb = unreadResults.stream()
                    .collect(Collectors.toMap(
                            arr -> (Long) arr[0],  // friendId
                            arr -> (Long) arr[1]   // unreadCount
                    ));
            // 回写Redis（带过期时间）
            unreadFromDb.forEach((friendId, count) ->
                    redisServiceUtil.updateUnreadMsgCount(currentUserId, friendId, count)
            );
        }

        // 5. 批量查数据库（最后一条消息）
        Map<Long, PrivateMsgDTO> lastMsgFromDb = new HashMap<>();
        if (!missingLastMsgIds.isEmpty()) {
            List<Object[]> lastMsgResults = userRepository.findLastMessageByFriendIds(currentUserId, missingLastMsgIds);
            lastMsgFromDb = lastMsgResults.stream()
                    .collect(Collectors.toMap(
                            arr -> (Long) arr[0],                  // friendId
                            arr -> {
                                LocalDateTime localDateTime = null;
                                if (arr[2] != null) {
                                    localDateTime = ((Timestamp) arr[2]).toLocalDateTime();
                                }
                                return new PrivateMsgDTO(          // 内容+时间
                                        (String) arr[1],
                                        localDateTime
                                );
                            }
                    ));
            // 回写Redis（带过期时间）
            lastMsgFromDb.forEach((friendId, msg) ->
                    redisServiceUtil.updateLastMsg(msg, currentUserId, friendId)
            );
        }

        // 6. 合并Redis和数据库的结果
        Map<Long, Long> finalUnreadMap = Stream.concat(unreadMap.entrySet().stream(), unreadFromDb.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Long, PrivateMsgDTO> finalLastMsgMap = Stream.concat(lastMsgMap.entrySet().stream(), lastMsgFromDb.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 7. 组装最终VO（好友基础信息 + 未读数 + 最后消息）
        return basicInfos.stream()
                .map(basic -> {
                    FriendSearchListVO vo = new FriendSearchListVO();
                    vo.setUserId(basic.getUserId());
                    vo.setUsername(basic.getUsername());
                    vo.setAvatar(basic.getAvatar());

                    // 未读数（默认0）
                    vo.setUnreadCount(finalUnreadMap.getOrDefault(basic.getUserId(), 0L));

                    // 最后一条消息（默认空）
                    PrivateMsgDTO lastMsg = finalLastMsgMap.get(basic.getUserId());
                    if (lastMsg != null) {
                        vo.setLastMessageContent(lastMsg.getContent());
                        vo.setLastMessageTime(lastMsg.getTime());
                    } else {
                        vo.setLastMessageContent("");
                        vo.setLastMessageTime(null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }



    // 2. 发送好友申请
    public void sendFriendApply(Long currentUserId, Long targetUserId, String remark) {
        // 2.1 检查目标用户是否存在
        UserInfo targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new FriendsException("目标用户不存在"));
        if (!targetUser.getEnabled()) {
            throw new FriendsException("目标用户已禁用");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new FriendsException("不能添加自己为好友");
        }
        // 2.2 检查是否已经是好友或有pending申请
        if (friendRelationRepository.hasRelationBetweenUsers(currentUserId, targetUserId, FriendStatus.ACCEPTED)) {
            throw new FriendsException("已经是好友，无需重复申请");
        }

        if (friendRelationRepository.hasSentPendingApply(currentUserId, targetUserId, FriendStatus.PENDING) ||
                friendRelationRepository.hasReceivedPendingApply(targetUserId, currentUserId, FriendStatus.PENDING)) {
            throw new FriendsException("已存在未处理的申请");
        }
        // 2.3 创建pending申请
        FriendRelation application = FriendRelation.builder()
                .user(userRepository.findById(currentUserId).orElseThrow())
                .friend(targetUser)
                .status(FriendStatus.PENDING)
                .remark(remark)
                .build();
        friendRelationRepository.save(application);
        log.info("发送好友申请：{} -> {}", currentUserId, targetUserId);
    }

    // 3. 同意好友申请
    public void acceptFriendApply(Long applicationId, Long currentUserId) {
        // 1. 根据applicationId获取好友关系实体（申请记录）
        FriendRelation application = friendRelationRepository.findById(applicationId)
                .orElseThrow(() -> new FriendsException("好友申请不存在"));

        // 2. 验证权限：当前用户必须是申请的「被动方」（即申请的接收者）
        UserInfo passiveUser = application.getFriend(); // FriendRelation的friend是被动方
        Assert.isTrue(passiveUser.getUserId().equals(currentUserId),
                "无权限处理该申请（你不是申请接收者）");

        // 3. 验证状态：申请必须是PENDING状态
        Assert.isTrue(application.getStatus() == FriendStatus.PENDING,
                "申请已处理，无法再次操作");

        // 4. 更新状态为ACCEPTED（成为好友）
        application.setStatus(FriendStatus.ACCEPTED);
        friendRelationRepository.save(application);
        log.info("处理好友申请：{} -> {}", currentUserId, passiveUser.getUserId());

        // TODO:初始化好友聊天频道/通知等逻辑
    }

    // 4. 删除好友
    public void deleteFriend(Long currentUserId, Long friendId) {
        // 4.1 检查是否存在好友关系
        if (!friendRelationRepository.hasSentPendingApply(currentUserId, friendId, FriendStatus.ACCEPTED) &&
                !friendRelationRepository.hasReceivedPendingApply(friendId, currentUserId, FriendStatus.ACCEPTED)) {
            throw new FriendsException("未添加该好友");
        }
        // 4.2 删除双向关系
        friendRelationRepository.deleteFriendship(currentUserId, friendId);
        log.info("删除好友关系：{} -> {}", currentUserId, friendId);
        //TODO: 删除好友的聊天频道/通知等逻辑
    }

    // 5. 获取好友信息（含未读数）
    public FriendInfoVO getFriendInfo(Long currentUserId, Long friendId) {
        // 5.1 查找好友关系
        if (!friendRelationRepository.hasRelationBetweenUsers(currentUserId, friendId, FriendStatus.ACCEPTED)){
            throw new FriendsException("未添加该好友");
        }
        // 5.2 获取好友基本信息
        UserInfo friend = userRepository.findById(friendId).orElseThrow();
        // 5.3 统计未读数
        log.info("获取好友信息：{} -> {}", currentUserId, friendId);
        return FriendInfoVO.builder()
                .friendId(friendId)
                .username(friend.getUsername())
                .avatar(friend.getAvatar())
                .email(friend.getEmail())
                .phone(friend.getPhone())
                .createTime(friend.getCreateTime())
                .status(FriendStatus.ACCEPTED)
                .build();
    }


}
