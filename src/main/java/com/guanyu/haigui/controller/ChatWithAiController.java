package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.AfterFirstChatDto;
import com.guanyu.haigui.pojo.dto.ChatRoomListDetailDto;
import com.guanyu.haigui.pojo.dto.FirstChatDto;
import com.guanyu.haigui.pojo.vo.ChatListVO;
import com.guanyu.haigui.pojo.vo.FirstChatVo;
import com.guanyu.haigui.pojo.vo.getAIChatListDetailVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
     * 获取某个聊天室内容
     */
    @Operation(summary = "获取某个聊天室内容")
    @PostMapping("/ChatRoomListDetail")
    public Result<getAIChatListDetailVO> getChatRoomListDetail(@RequestBody ChatRoomListDetailDto chatRoomListDetailDto) {
        return Result.success(chatService.getAIChatListDetail(
                chatRoomListDetailDto.getRoomId(),
                chatRoomListDetailDto.getGameSessionId()));
    }

    @Operation(summary = "获取海龟汤游玩列表接口")
    @GetMapping("/getChatRoomList")
    public List<ChatListVO> getChatRoomListWithLastMessage() {
        return chatService.getAIChatList(BaseContext.getCurrentId());
    }
}
