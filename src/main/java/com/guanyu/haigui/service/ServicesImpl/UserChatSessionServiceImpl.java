package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.model.UserChatSession;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.ChatSessionPageVO;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.repository.UserChatSessionRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.UserChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserChatSessionServiceImpl implements UserChatSessionService {

    private static final String TYPE_PRIVATE = "PRIVATE";
    private static final String TYPE_GROUP = "GROUP";

    private final UserChatSessionRepository sessionRepository;
    private final UserInfoRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionVO> listStickySessions(Long userId) {
        return sessionRepository.findByUserIdAndIsStickyTrueOrderByLastMessageTimeDescIdDesc(userId)
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionPageVO listNonStickySessions(Long userId, int pageSize, String cursor) {
        int limit = Math.min(Math.max(pageSize, 1), 50);
        List<UserChatSession> rows;

        if (cursor == null || cursor.isBlank()) {
            rows = sessionRepository.findNonStickyFirstPage(userId, PageRequest.of(0, limit + 1));
        } else {
            Cursor c = decodeCursor(cursor);
            rows = sessionRepository.findNonStickyAfterCursor(
                    userId, c.time(), c.id(), PageRequest.of(0, limit + 1));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        ChatSessionPageVO page = new ChatSessionPageVO();
        page.setList(rows.stream().map(this::toVo).collect(Collectors.toList()));
        page.setHasMore(hasMore);
        if (hasMore && !rows.isEmpty()) {
            UserChatSession last = rows.get(rows.size() - 1);
            page.setNextCursor(encodeCursor(last.getLastMessageTime(), last.getId()));
        } else {
            page.setNextCursor(null);
        }
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPrivateMessageSent(Long senderId, Long receiverId, String content, LocalDateTime time, String senderName) {
        UserInfo sender = userRepository.findById(senderId).orElse(null);
        UserInfo receiver = userRepository.findById(receiverId).orElse(null);
        if (sender == null || receiver == null) {
            return;
        }

        upsertPrivateRow(senderId, receiverId, receiver.getUsername(), receiver.getAvatar(),
                content, time, null, false, 0L);
        upsertPrivateRow(receiverId, senderId, sender.getUsername(), sender.getAvatar(),
                content, time, senderName, true, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onGroupMessageSent(String groupId, Long senderId, String content, LocalDateTime time, String senderName) {
        sessionRepository.incrementGroupUnreadExcept(groupId, senderId);
        sessionRepository.updateGroupLastMessageForAll(groupId, content, time, senderName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPrivateUnread(Long userId, Long peerId) {
        sessionRepository.findByUserIdAndSessionIdAndChatType(userId, String.valueOf(peerId), TYPE_PRIVATE)
                .ifPresent(row -> {
                    row.setUnreadCount(0L);
                    sessionRepository.save(row);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearGroupUnread(Long userId, String groupId) {
        sessionRepository.findByUserIdAndSessionIdAndChatType(userId, groupId, TYPE_GROUP)
                .ifPresent(row -> {
                    row.setUnreadCount(0L);
                    sessionRepository.save(row);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSticky(Long userId, String sessionId, String chatType, boolean isSticky) {
        sessionRepository.findByUserIdAndSessionIdAndChatType(userId, sessionId, chatType.toUpperCase())
                .ifPresent(row -> {
                    row.setIsSticky(isSticky);
                    sessionRepository.save(row);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureGroupSession(Long userId, String groupId, String groupName, String groupAvatar) {
        UserChatSession row = sessionRepository
                .findByUserIdAndSessionIdAndChatType(userId, groupId, TYPE_GROUP)
                .orElseGet(() -> UserChatSession.builder()
                        .userId(userId)
                        .sessionId(groupId)
                        .chatType(TYPE_GROUP)
                        .unreadCount(0L)
                        .isSticky(false)
                        .build());
        row.setChatName(groupName);
        row.setChatAvatar(groupAvatar != null ? groupAvatar : "");
        sessionRepository.save(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensurePrivateSession(Long userId, Long peerId, String peerName, String peerAvatar) {
        String peerKey = String.valueOf(peerId);
        UserChatSession row = sessionRepository
                .findByUserIdAndSessionIdAndChatType(userId, peerKey, TYPE_PRIVATE)
                .orElseGet(() -> UserChatSession.builder()
                        .userId(userId)
                        .sessionId(peerKey)
                        .chatType(TYPE_PRIVATE)
                        .unreadCount(0L)
                        .isSticky(false)
                        .build());
        row.setChatName(peerName);
        row.setChatAvatar(peerAvatar);
        sessionRepository.save(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeGroupSession(Long userId, String groupId) {
        sessionRepository.deleteByUserAndSession(userId, groupId, TYPE_GROUP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAllGroupSessions(String groupId) {
        sessionRepository.deleteAllGroupSessions(groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePrivateChatBetween(Long userId1, Long userId2) {
        sessionRepository.deleteByUserAndSession(userId1, String.valueOf(userId2), TYPE_PRIVATE);
        sessionRepository.deleteByUserAndSession(userId2, String.valueOf(userId1), TYPE_PRIVATE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroupName(String groupId, String groupName) {
        sessionRepository.updateGroupName(groupId, groupName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroupAvatar(String groupId, String groupAvatar) {
        sessionRepository.updateGroupAvatar(groupId, groupAvatar);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ChatSessionVO> mapPrivateSessions(Long userId, List<Long> friendIds) {
        if (CollectionUtils.isEmpty(friendIds)) {
            return Collections.emptyMap();
        }
        List<String> ids = friendIds.stream().map(String::valueOf).collect(Collectors.toList());
        return sessionRepository.findByUserIdAndChatTypeAndSessionIdIn(userId, TYPE_PRIVATE, ids)
                .stream()
                .collect(Collectors.toMap(
                        s -> Long.parseLong(s.getSessionId()),
                        this::toVo,
                        (a, b) -> a));
    }

    private void upsertPrivateRow(
            Long userId,
            Long peerId,
            String peerName,
            String peerAvatar,
            String content,
            LocalDateTime time,
            String lastSenderName,
            boolean incrementUnread,
            Long forceUnread) {
        String peerKey = String.valueOf(peerId);
        UserChatSession row = sessionRepository
                .findByUserIdAndSessionIdAndChatType(userId, peerKey, TYPE_PRIVATE)
                .orElseGet(() -> UserChatSession.builder()
                        .userId(userId)
                        .sessionId(peerKey)
                        .chatType(TYPE_PRIVATE)
                        .unreadCount(0L)
                        .isSticky(false)
                        .build());

        row.setChatName(peerName);
        row.setChatAvatar(peerAvatar);
        row.setLastMessageContent(content);
        row.setLastMessageTime(time);
        row.setLastSenderName(lastSenderName);

        if (forceUnread != null) {
            row.setUnreadCount(forceUnread);
        } else if (incrementUnread) {
            row.setUnreadCount((row.getUnreadCount() != null ? row.getUnreadCount() : 0L) + 1);
        }

        sessionRepository.save(row);
    }

    private ChatSessionVO toVo(UserChatSession s) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setSessionId(s.getSessionId());
        vo.setChatType(s.getChatType());
        vo.setChatName(s.getChatName());
        vo.setChatAvatar(s.getChatAvatar());
        vo.setUnreadCount(s.getUnreadCount() != null ? s.getUnreadCount() : 0L);
        vo.setLastMessageContent(s.getLastMessageContent());
        vo.setLastMessageTime(s.getLastMessageTime());
        vo.setIsSticky(Boolean.TRUE.equals(s.getIsSticky()));
        vo.setLastSenderName(s.getLastSenderName());
        return vo;
    }

    private record Cursor(LocalDateTime time, Long id) {}

    private static String encodeCursor(LocalDateTime time, Long id) {
        String raw = (time != null ? time.toString() : "") + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        int idx = raw.lastIndexOf('|');
        if (idx < 0) {
            throw new IllegalArgumentException("invalid cursor");
        }
        String timePart = raw.substring(0, idx);
        Long id = Long.parseLong(raw.substring(idx + 1));
        LocalDateTime time = timePart.isEmpty() ? null : LocalDateTime.parse(timePart);
        return new Cursor(time, id);
    }
}
