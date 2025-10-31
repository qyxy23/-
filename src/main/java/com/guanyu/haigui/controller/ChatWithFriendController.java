package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.dto.getPrivateHistoryMessagesDTO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import com.guanyu.haigui.service.MessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@AllArgsConstructor
@Controller
@Tag(name = "私信聊天接口", description = "聊天相关接口")
public class ChatWithFriendController {
    private final MessageService messageService;


    /*
    处理客户端发送的消息
     */
    @MessageMapping("/chat.sendMessage")
    public PrivateMessageVO sendMessage(@Payload PrivateMessageDTO message) {
        return messageService.sendMessage(message);
    }


    /*
    处理客户端发送的消息(测试)
     */
    @PostMapping("/chat/sendMessage")
    @ResponseBody
    public PrivateMessageVO TextSendMessage(@RequestBody PrivateMessageDTO message) {
        return messageService.sendMessage(message);
    }

    /*
    * 获取两个用户之间的历史消息
     */
    @PostMapping("/chat/getHistoryMessages")
    @ResponseBody
    Page<PrivateMessageVO> getHistoryMessages(@RequestBody getPrivateHistoryMessagesDTO message){
        Long userId = BaseContext.getCurrentId();
        return messageService.getHistoryMessages(userId, message.getReceiverId(), message.getPage(), message.getSize());
    }

}