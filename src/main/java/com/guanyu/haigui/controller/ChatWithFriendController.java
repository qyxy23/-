package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.dto.TopSessionRequest;
import com.guanyu.haigui.pojo.dto.getPrivateHistoryMessagesDTO;
import com.guanyu.haigui.pojo.vo.ChatSessionVO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@AllArgsConstructor
@Controller
@Tag(name = "私信聊天接口", description = "聊天相关接口")
public class ChatWithFriendController {
    private final MessageService messageService;

    @GetMapping("/chat/getChatSessions")
    @ResponseBody
    @Operation(summary = "获取聊天会话列表")
    public List<ChatSessionVO> getChatSessions() {
        return messageService.getChatSessions();
    }

    /**
     * 置顶/取消置顶单个会话
     * @param request 包含sessionId、chatType、isSticky（可选）
     * @return 操作结果
     */
    @PostMapping("/topSingleSession")
    @Operation(summary = "置顶/取消置顶单个会话")
    @ResponseBody
    public Result<String> topSingleSession(@RequestBody TopSessionRequest request) {
        messageService.topSingleSession(request.getSessionId(), request.getChatType(), request.getIsSticky());
        return Result.success("置顶状态更新成功");
    }



    /*
    处理客户端发送的消息
     */
    @MessageMapping("/chat.sendMessage")
    @Operation(summary = "发送消息")
    public PrivateMessageVO sendMessage(@Payload PrivateMessageDTO message,@Header("simpSessionId") String sessionId) {
        return messageService.sendMessage(message,sessionId);
    }


    /*
    处理客户端发送的消息(测试)
     */
    @PostMapping("/chat/sendMessage")
    @ResponseBody
    @Operation(summary = "发送消息")
    public PrivateMessageVO TextSendMessage(@RequestBody PrivateMessageDTO message) {
        return messageService.sendMessage(message);
    }

    /*
    * 获取两个用户之间的历史消息
     */
    @PostMapping("/chat/getHistoryMessages")
    @ResponseBody
    @Operation(summary = "获取两个用户之间的历史消息")
    Page<PrivateMessageVO> getHistoryMessages(@RequestBody getPrivateHistoryMessagesDTO message){
        Long userId = BaseContext.getCurrentId();
        return messageService.getHistoryMessages(userId, message.getReceiverId(), message.getPage(), message.getSize());
    }

}