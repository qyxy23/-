package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.Assert;
import com.guanyu.haigui.Enum.FriendStatus;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.FriendRelation;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.FriendInfoVO;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import com.guanyu.haigui.repository.FriendRelationRepository;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.FriendsService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
@Slf4j
@AllArgsConstructor
@Service
public class FriendsServiceImpl implements FriendsService {

    private final UserInfoRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final PrivateMessageRepository messageRepository;


    // 1. 搜索潜在的好友
    public List<FriendSearchResultVO> searchFriends(String keyword) {
        if (keyword == null) {
            throw new RuntimeException("搜索关键字不能为空");
        }
        Long currentUserId = BaseContext.getCurrentId();
        // 1.1 搜索潜在好友（排除自己）
        List<FriendSearchResultVO> potentialFriends = userRepository.searchPotentialFriends(keyword, currentUserId);

        // 1.2 过滤：排除已有好友、已发/已收pending申请的用户
        List<FriendSearchResultVO> filteredFriends = potentialFriends.stream()
                // 排除已经是ACCEPTED好友的用户
                .filter(user -> !friendRelationRepository.hasRelationBetweenUsers(currentUserId, user.getUserId(), FriendStatus.ACCEPTED))
                // 排除当前用户已向对方发送pending申请的情况
                .filter(user -> !friendRelationRepository.hasSentPendingApply(currentUserId, user.getUserId(), FriendStatus.PENDING))
                // 排除对方已向当前用户发送pending申请的情况
                .filter(user -> !friendRelationRepository.hasReceivedPendingApply(user.getUserId(), currentUserId, FriendStatus.PENDING))
                .toList();
        log.info("搜索好友：{}", keyword);

        // 1.3 构造结果：包含最后一条消息和未读数
        return filteredFriends;
    }

    /**
     * 搜索好友列表
     */
    public List<FriendSearchListVO> searchFriendsList(){
        // 1.1 搜索好友
        long currentUserId = BaseContext.getCurrentId();
        List<FriendSearchListVO> potentialFriends = userRepository.findFriendInfoWithMessages(currentUserId);

        // 1.2 过滤：排除已有好友、已发/已收pending申请的用户
        // List<FriendSearchListVO> filteredFriends = potentialFriends.stream()
        //         // 排除已经是ACCEPTED好友的用户
        //         .filter(user -> !friendRelationRepository.hasRelationBetweenUsers(currentUserId, user.getUserId(), FriendStatus.ACCEPTED))
        //         // 排除当前用户已向对方发送pending申请的情况
        //         .filter(user -> !friendRelationRepository.hasSentPendingApply(currentUserId, user.getUserId(), FriendStatus.PENDING))
        //         // 排除对方已向当前用户发送pending申请的情况
        //         .filter(user -> !friendRelationRepository.hasReceivedPendingApply(user.getUserId(), currentUserId, FriendStatus.PENDING))
        //         .toList();
        log.info("搜索好友列表{}",potentialFriends);
        return potentialFriends;
        // 1.3 构造结果：包含最后一条消息和未读数
        // return filteredFriends.stream().map(user -> {
        //     // 查询与当前用户的最后一条消息
        //     Optional<PrivateMessage> lastMsg = messageRepository.findLastMessageBetweenUsers(currentUserId, user.getUserId());
        //     // 统计当前用户未读的消息数（接收者为当前用户，发送者为该好友）
        //     Long unread = messageRepository.countByReceiverUserIdAndSenderUserIdAndIsReadFalse(currentUserId, user.getUserId());
        //     // 转换为VO
        //     return FriendSearchResultVO.builder()
        //             .userInfo(user)
        //             .lastMessage(lastMsg.orElse(null))
        //             .unreadCount(unread)
        //             .build();
        // }).collect(Collectors.toList());
    }



    // 2. 发送好友申请
    public void sendFriendApply(Long currentUserId, Long targetUserId, String remark) {
        // 2.1 检查目标用户是否存在
        UserInfo targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("目标用户不存在"));
        // 2.2 检查是否已经是好友或有pending申请
        if (friendRelationRepository.hasRelationBetweenUsers(currentUserId, targetUserId, FriendStatus.ACCEPTED)) {
            throw new RuntimeException("已经是好友，无需重复申请");
        }

        if (friendRelationRepository.hasSentPendingApply(currentUserId, targetUserId, FriendStatus.PENDING) ||
                friendRelationRepository.hasReceivedPendingApply(targetUserId, currentUserId, FriendStatus.PENDING)) {
            throw new RuntimeException("已存在未处理的申请");
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
                .orElseThrow(() -> new RuntimeException("好友申请不存在"));

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
            throw new RuntimeException("未添加该好友");
        }
        // 4.2 删除双向关系
        friendRelationRepository.deleteFriendship(currentUserId, friendId);
        log.info("删除好友关系：{} -> {}", currentUserId, friendId);
        //TODO: 删除好友的聊天频道/通知等逻辑
    }

    // 5. 获取好友信息（含未读数）
    public FriendInfoVO getFriendInfo(Long currentUserId, Long friendId) {
        // 5.1 查找好友关系
        FriendRelation relation = friendRelationRepository.findByUserUserIdAndFriendUserIdAndStatus(currentUserId, friendId, FriendStatus.ACCEPTED)
                .orElseThrow(() -> new RuntimeException("未添加该好友"));
        // 5.2 获取好友基本信息
        UserInfo friend = userRepository.findById(friendId).orElseThrow();
        // 5.3 统计未读数
        Long unread = messageRepository.countByReceiverUserIdAndSenderUserIdAndIsReadFalse(currentUserId, friendId);
        log.info("获取好友信息：{} -> {}", currentUserId, friendId);
        return FriendInfoVO.builder()
                .friendId(friendId)
                .nickname(friend.getUsername())
                .avatar(friend.getAvatar())
                .remark(relation.getRemark())
                .status(relation.getStatus())
                .unreadCount(unread)
                .build();
    }

}
