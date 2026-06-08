package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.ChatMessagesAfterDTO;
import com.guanyu.haigui.pojo.dto.ClearChatHistoryDTO;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.dto.TopSessionRequest;
import com.guanyu.haigui.pojo.dto.getPrivateHistoryMessagesDTO;
import com.guanyu.haigui.pojo.vo.ChatSessionPageVO;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@AllArgsConstructor
@Controller
@Tag(name = "私信聊天接口", description = "聊天相关接口")
public class ChatWithFriendController {
    private final MessageService messageService;

    /**
     * 分页获取聊天会话列表
     * stickyOnly=true：返回全部置顶会话；否则按 cursor 分页返回非置顶会话
     */
    @GetMapping("/chat/getChatSessions")
    @ResponseBody
    @Operation(summary = "获取聊天会话列表（分页）")
    public ChatSessionPageVO getChatSessions(
            @RequestParam(required = false) Boolean stickyOnly,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String cursor) {
        if (Boolean.TRUE.equals(stickyOnly)) {
            ChatSessionPageVO page = new ChatSessionPageVO();
            page.setList(messageService.getStickySessions());
            page.setHasMore(false);
            page.setNextCursor(null);
            return page;
        }
        return messageService.getNonStickySessions(pageSize, cursor);
    }

    @PostMapping("/chat/topSingleSession")
    @Operation(summary = "置顶/取消置顶单个会话")
    @ResponseBody
    public Result<String> topSingleSession(@RequestBody TopSessionRequest request) {
        messageService.topSingleSession(request.getSessionId(), request.getChatType(), request.getIsSticky());
        return Result.success("置顶状态更新成功");
    }

    @MessageMapping("/chat.sendMessage")
    @Operation(summary = "发送消息")
    public PrivateMessageVO sendMessage(@Payload PrivateMessageDTO message, @Header("simpSessionId") String sessionId) {
        return messageService.sendMessage(message, sessionId);
    }

    @PostMapping("/chat/sendMessage")
    @ResponseBody
    @Operation(summary = "发送消息")
    public PrivateMessageVO TextSendMessage(@RequestBody PrivateMessageDTO message) {
        return messageService.sendMessage(message);
    }

    @PostMapping("/chat/getHistoryMessages")
    @ResponseBody
    @Operation(summary = "获取两个用户之间的历史消息")
    Page<PrivateMessageVO> getHistoryMessages(@RequestBody getPrivateHistoryMessagesDTO message) {
        Long userId = BaseContext.getCurrentId();
        return messageService.getHistoryMessages(userId, message.getReceiverId(), message.getPage(), message.getSize());
    }

    @PostMapping("/chat/clearUnread")
    @ResponseBody
    @Operation(summary = "清零私聊未读数并推进游标")
    public Result<String> clearPrivateUnread(@RequestBody getPrivateHistoryMessagesDTO message) {
        messageService.clearPrivateSessionUnread(message.getReceiverId());
        return Result.success("已读");
    }

    @PostMapping("/chat/messagesAfter")
    @ResponseBody
    @Operation(summary = "增量拉取私聊消息（afterTime 之后）")
    public Result<List<PrivateMessageVO>> getPrivateMessagesAfter(@RequestBody ChatMessagesAfterDTO dto) {
        return Result.success(messageService.getPrivateMessagesAfter(dto));
    }

    @PostMapping("/chat/clearHistory")
    @ResponseBody
    @Operation(summary = "清空私聊聊天记录（账号级边界）")
    public Result<String> clearPrivateHistory(@RequestBody ClearChatHistoryDTO dto) {
        messageService.clearPrivateChatHistory(Long.parseLong(dto.getSessionId()));
        return Result.success("已清空");
    }
}
