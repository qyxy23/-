package com.guanyu.haigui.websocket;

import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
@AllArgsConstructor
public class PrivateChatService {
    private final PrivateMessageRepository privateMessageRepository;
    private final UserInfoRepository userInfoRepository;

    /**
     * 获取与某用户的所有聊天记录（分页）
     */
    public Page<PrivateMessage> getChatHistoryWithUser(
            Long currentUserId,
            Long targetUserId,
            int page,
            int size
    ) {
        // 1. 获取当前用户和目标用户实体
        UserInfo currentUser = userInfoRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
        UserInfo targetUser = userInfoRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));

        // 2. 构造分页请求（按时间倒序）
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createTime");

        // 3. 查询两人之间的对话流
        return privateMessageRepository.findConversationBetweenUsers(currentUser, targetUser, pageable);
    }

    /**
     * 获取当前用户的未读私聊数量
     */
    public long getUnreadPrivateMessageCount(Long userId) {
        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return privateMessageRepository.countByReceiverAndIsReadFalse(user);
    }

    /**
     * 标记某条消息为已读
     */
    public void markMessageAsRead(String messageId) {
        privateMessageRepository.markMessagesAsRead(List.of(messageId));
    }
}
