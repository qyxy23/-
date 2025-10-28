package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.GroupMessageVO;
import com.guanyu.haigui.pojo.vo.LobbyListVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.websocket.LobbyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Tag(name = "大厅聊天接口", description = "大厅相关接口")
@Slf4j
@AllArgsConstructor
@Controller
public class ChatWithFriendsController {
    private final LobbyService lobbyService;

    /**
     * 创建游戏房间
     *
     * @param request   创建房间请求参数
     */
    @Operation(summary = "创建大厅")
    @ResponseBody
    @PostMapping("/createLobby")
    public Result<String> createLobby(@RequestBody CreateRoomRequest request) {
        // 创建大厅并获取生成的房间ID
        return Result.success(lobbyService.createChatRoom(request));
    }

    @Operation(summary = "搜索自己已经加入的大厅")
    @GetMapping("/mineLobby")
    @ResponseBody
    public Result<List<ChatRoomDTO>> getMineLobbies() {
        // 获取当前用户创建的大厅
        return Result.success(lobbyService.getMineLobbies());
    }

    /**
     * 1. 客户端发送消息到：/app/chat/searchLobbies（需与@Configuration中配置的/app前缀匹配）
     * 2. 消息体为SearchLobbiesMessage（包含dto和page）
     * 3. 服务端处理后，将分页结果发送到主题：/topic/searchLobbies_result（前端需订阅该主题）
     */
    @Operation(summary = "搜索游戏大厅")
    @PostMapping("/searchLobbies")
    @ResponseBody
    public PageImpl<LobbyListVO> searchLobbies(@RequestBody SearchLobbiesMessage message) {
        // 解析参数
        LobbyListDTO dto = message.getDto();
        int page = message.getPage();

        // 调用原分页查询方法
        return lobbyService.searchLobbies(dto, page);
    }


    // 获取指定房间的历史消息
    @Operation(summary = "获取指定房间的历史消息")
    @PostMapping("/chat.history")
    @ResponseBody
    public Page<GroupMessageVO> getRoomChatHistory(@RequestBody RoomChatHistoryDTO roomChatHistoryDTO) {
        return lobbyService.getGroupMessages(roomChatHistoryDTO);
    }







    // 处理用户加入大厅的请求（前端发送到/app/chat.joinLobby）
    @Operation(summary = "处理用户加入大厅的请求")
    @MessageMapping("/ws/joinRoom")
    public void joinRoom(SimpMessageHeaderAccessor accessor, @Payload JoinChatRoomRequest request) {
        String sessionId = accessor.getSessionId();
        String lobbyId = request.getChatRoomId();
        // 加入大厅
        lobbyService.joinChatRoom(lobbyId, sessionId);
    }


    @Operation(summary = "获取指定房间的最新N条消息")
    @MessageMapping("/chat.recent/{roomId}/{limit}") // 接收请求：包含roomId和limit
    @SendTo("/topic/recent/{roomId}") // 广播结果：发送到对应房间的Topic（仅订阅该房间的客户端能收到）
    public List<GroupMessageVO> getRecentMessages(
            @DestinationVariable String roomId, // 从路径变量获取roomId
            @DestinationVariable int limit) { // 从路径变量获取limit（带校验）
        return lobbyService.getRecentMessages(roomId, limit);
    }


    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @MessageMapping("/ws/sendLobbyMessage")
    public void sendLobbyMessage(@Payload SendGroupMessageRequest message, @Header("simpSessionId") String sessionId) {
        lobbyService.sendLobbyMessage(message, sessionId);
    }


    @Operation(summary = "离开大厅")
    @MessageMapping("/leaveLobby")
    public void leaveLobby(@Payload String roomId, @Header("simpSessionId") String sessionId) {
        // 离开大厅
        lobbyService.leaveLobby(roomId, sessionId);
    }

    // 处理大厅人数是否达标，若达到要求可开始游戏
    // @Operation(summary = "处理大厅人数是否达标，若达到要求可开始游戏")
    // @MessageMapping("/checkRoomStatus")
    // public void checkRoomStatus(@Payload String roomId) {
    //     log.info("检查房间{}的状态", roomId);
    //     lobbyService.checkRoomStatus(roomId);
    // }

    // /**
    // * 处理消息发送请求
    // * @param request 消息参数
    // * @param sessionId WebSocket会话ID（用于获取发送者）
    // */
    // @Operation(summary = "处理发送消息的请求")
    // @MessageMapping("/ws/sendGameMessage") //
    // // 前端发送到/app/ws/sendMessage（需结合WebSocketConfig的applicationDestinationPrefixes）
    // public void sendGameMessage(
    //         @Validated SendMessageRequest request,
    //         @Header("simpSessionId") String sessionId) { // 从WebSocket头获取SessionID
    //     lobbyService.sendGameMessage(request, sessionId);
    // }



}
