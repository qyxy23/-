package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.model.GroupMessage;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.repository.ChatGroupMessageRepository;
import com.guanyu.haigui.repository.PrivateMessageRepository;
import com.guanyu.haigui.utils.ClientMsgIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatSendIdempotencyService {

    private final PrivateMessageRepository privateMessageRepository;
    private final ChatGroupMessageRepository chatGroupMessageRepository;

    public String normalizeClientMsgId(String raw) {
        return ClientMsgIdUtil.normalize(raw);
    }

    public Optional<PrivateMessage> findExistingPrivate(Long senderId, Long receiverId, String clientMsgId) {
        String normalized = ClientMsgIdUtil.normalize(clientMsgId);
        if (normalized == null || senderId == null || receiverId == null) {
            return Optional.empty();
        }
        return privateMessageRepository.findBySender_UserIdAndReceiver_UserIdAndClientMsgId(
                senderId, receiverId, normalized);
    }

    public Optional<GroupMessage> findExistingGroup(String groupId, Long senderId, String clientMsgId) {
        String normalized = ClientMsgIdUtil.normalize(clientMsgId);
        if (normalized == null || groupId == null || groupId.isBlank() || senderId == null) {
            return Optional.empty();
        }
        return chatGroupMessageRepository.findByGroupAndSenderAndClientMsgId(groupId, senderId, normalized);
    }

}
