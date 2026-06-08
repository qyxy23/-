package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.ChatMessagesAfterDTO;
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

    /** 离开私聊页时清零未读数并推进游标 */
    void clearPrivateSessionUnread(Long friendId);

    /** 增量拉取私聊消息（afterTime 之后，升序） */
    List<PrivateMessageVO> getPrivateMessagesAfter(ChatMessagesAfterDTO dto);

    /** 清空私聊本地可见历史边界（账号级） */
    void clearPrivateChatHistory(Long friendId);

    /** 硬删除两用户之间的私聊消息、Inbox 与 Redis 缓存 */
    void hardDeletePrivateChatBetween(Long userId1, Long userId2);
}
