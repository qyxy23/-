package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 聊天接口
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/chat")
@Tag(name = "聊天接口", description = "聊天相关接口")
public class ChatWithAiController {
    @Resource
    private ChatService chatService;


    /**
     * 聊天
     */
    @Operation(summary = "第一次聊天")
    @PostMapping
    public Result<FirstChatVo> doFirstChat(@RequestBody FirstChatDto firstChatDto) {
        return Result.success(chatService.doFirstChatWithAi(firstChatDto.getMessage()));
    }

    @Operation(summary = "后续聊天")
    @PostMapping("/afterFirst")
    public Result<String> doChat(@RequestBody AfterFirstChatDto afterFirstChatDto) {
        return Result.success(chatService.chatWithAI(afterFirstChatDto.getRoomId(), afterFirstChatDto.getMessage()));
    }

    /**
     * 获取聊天室列表，包含最后一条消息
     */
    @Operation(summary = "获取聊天室列表内容")
    @GetMapping()
    public List<ChatRoomListVO> getChatRoomList() {
        return chatService.getAIChatRoomListWithLastMessage(BaseContext.getCurrentId());
    }

    /**
     * 获取某个聊天室内容
     */
    @Operation(summary = "获取某个聊天室内容")
    @PostMapping("/ChatRoomListDetail")
    public Result<ChatRoomListDetailVO> getChatRoomListDetail(@RequestBody ChatRoomListDetailDto chatRoomListDetailDto) {
        return Result.success(chatService.getAIChatRoomListDetail(chatRoomListDetailDto.getSessionId()));
    }






}
