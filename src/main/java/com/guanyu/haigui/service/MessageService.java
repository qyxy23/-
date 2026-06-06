package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.vo.ChatSessionPageVO;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface MessageService {

    PrivateMessageVO sendMessage(PrivateMessageDTO message, String sessionId);

    PrivateMessageVO sendMessage(PrivateMessageDTO message);

    Page<PrivateMessageVO> getHistoryMessages(Long userId1, Long userId2, int page, int size);

    List<ChatSessionVO> getStickySessions();

    ChatSessionPageVO getNonStickySessions(int pageSize, String cursor);

    void topSingleSession(String sessionId, String chatType, Boolean isSticky);

    /** 硬删除两用户之间的私聊消息、Inbox 与 Redis 缓存 */
    void hardDeletePrivateChatBetween(Long userId1, Long userId2);
}
