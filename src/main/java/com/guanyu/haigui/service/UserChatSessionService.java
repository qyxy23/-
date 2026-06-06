package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.ChatSessionPageVO;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserChatSessionService {

    List<ChatSessionVO> listStickySessions(Long userId);

    ChatSessionPageVO listNonStickySessions(Long userId, int pageSize, String cursor);

    void onPrivateMessageSent(Long senderId, Long receiverId, String content, LocalDateTime time, String senderName);

    void onGroupMessageSent(String groupId, Long senderId, String content, LocalDateTime time, String senderName);

    void clearPrivateUnread(Long userId, Long peerId);

    void clearGroupUnread(Long userId, String groupId);

    void setSticky(Long userId, String sessionId, String chatType, boolean isSticky);

    void ensureGroupSession(Long userId, String groupId, String groupName, String groupAvatar);

    void ensurePrivateSession(Long userId, Long peerId, String peerName, String peerAvatar);

    void removeGroupSession(Long userId, String groupId);

    void removeAllGroupSessions(String groupId);

    /** 删除双方私聊 Inbox 会话 */
    void removePrivateChatBetween(Long userId1, Long userId2);

    void updateGroupName(String groupId, String groupName);

    void updateGroupAvatar(String groupId, String groupAvatar);

    Map<Long, ChatSessionVO> mapPrivateSessions(Long userId, List<Long> friendIds);
}
