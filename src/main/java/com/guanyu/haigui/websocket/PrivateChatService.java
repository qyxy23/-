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

    public Page<PrivateMessage> getChatHistoryWithUser(
            Long currentUserId,
            Long targetUserId,
            int page,
            int size
    ) {
        UserInfo currentUser = userInfoRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
        UserInfo targetUser = userInfoRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("目标用户不存在"));

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createTime");
        return privateMessageRepository.findConversationBetweenUsers(currentUser, targetUser, pageable);
    }

    /** 未读改由 user_chat_session 维护，此处保留兼容接口 */
    public long getUnreadPrivateMessageCount(Long userId) {
        return 0L;
    }

    /** 未读改由 user_chat_session 游标维护 */
    public void markMessageAsRead(String messageId) {
        // no-op
    }
}
