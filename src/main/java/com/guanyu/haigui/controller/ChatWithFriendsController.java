package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.model.GroupMessage;
import com.guanyu.haigui.websocket.LobbyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Controller
public class ChatWithFriendsController {
    private final LobbyService lobbyService;

    /**
     * 创建游戏房间
     * @param request 创建房间请求参数
     * @param sessionId WebSocket会话ID（用于获取创建者）
     * @return 创建成功返回房间ID和会话ID
     */
    @Operation(summary = "创建大厅")
    @MessageMapping("/createLobby")
    public Map<String, String> createLobby(@Payload CreateRoomRequest request, @Header("simpSessionId") String sessionId) {
        // 创建大厅
        String roomId = lobbyService.createChatRoom(request, sessionId);
        // 返回房间ID和会话ID
        Map<String, String> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("sessionId", sessionId);
        return result;
    }


    /**
     * 1. 客户端发送消息到：/app/chat/searchLobbies（需与@Configuration中配置的/app前缀匹配）
     * 2. 消息体为SearchLobbiesMessage（包含dto和page）
     * 3. 服务端处理后，将分页结果发送到主题：/topic/searchLobbies_result（前端需订阅该主题）
     */
    @Operation(summary = "搜索游戏大厅")
    @MessageMapping("/searchLobbies")
    public void searchLobbies(@Payload SearchLobbiesMessage message) {
        // 解析参数
        LobbyListDTO dto = message.getDto();
        int page = message.getPage();

        // 调用原分页查询方法
        lobbyService.searchLobbies(dto, page);
    }



    // 处理用户加入大厅的请求（前端发送到/app/chat.joinLobby）
    @Operation(summary = "处理用户加入大厅的请求")
    @MessageMapping("/ws/joinRoom")
    public void joinRoom(SimpMessageHeaderAccessor accessor,@Payload JoinChatRoomRequest request) {
        String sessionId = accessor.getSessionId();
        String lobbyId = request.getChatRoomId();
        // 加入大厅
        lobbyService.joinChatRoom(lobbyId,sessionId);
    }


    // 获取指定房间的历史消息
    @Operation(summary = "获取指定房间的历史消息")
    @SubscribeMapping("/chat.history")
    public Page<GroupMessage> getRoomChatHistory(@Payload RoomChatHistoryDTO roomChatHistoryDTO) {
        return lobbyService.getGroupMessages(roomChatHistoryDTO);
    }

    @Operation(summary = "获取指定房间的最新N条消息")
    @MessageMapping("/chat.recent/{roomId}/{limit}") // 接收请求：包含roomId和limit
    @SendTo("/topic/recent/{roomId}") // 广播结果：发送到对应房间的Topic（仅订阅该房间的客户端能收到）
    public List<GroupMessage> getRecentMessages(
            @DestinationVariable @NotBlank String roomId, // 从路径变量获取roomId
            @DestinationVariable
            @Min(1) @Max(100) int limit) { // 从路径变量获取limit（带校验）
        return lobbyService.getRecentMessages(roomId, limit);
    }



    //处理大厅人数是否达标，若达到要求可开始游戏
    @Operation(summary = "处理大厅人数是否达标，若达到要求可开始游戏")
    @MessageMapping
    public void checkRoomStatus(@Payload String roomId) {
        log.info("检查房间{}的状态", roomId);
        lobbyService.checkRoomStatus(roomId);
    }



    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @MessageMapping("/ws/sendLobbyMessage")
    public void sendLobbyMessage(@Payload SendMessageRequest message, @Header("simpSessionId") String sessionId) {
        lobbyService.sendLobbyMessage(message,sessionId);
    }

    // /**
    //  * 处理消息发送请求
    //  * @param request 消息参数
    //  * @param sessionId WebSocket会话ID（用于获取发送者）
    //  */
    // @Operation(summary = "处理发送消息的请求")
    // @MessageMapping("/ws/sendGameMessage") // 前端发送到/app/ws/sendMessage（需结合WebSocketConfig的applicationDestinationPrefixes）
    // public void sendGameMessage(
    //         @Validated SendMessageRequest request,
    //         @Header("simpSessionId") String sessionId) { // 从WebSocket头获取SessionID
    //     lobbyService.sendGameMessage(request, sessionId);
    // }


    @Operation(summary = "离开大厅")
    @MessageMapping("/leaveLobby")
    public void leaveLobby(@Payload String roomId, @Header("simpSessionId") String sessionId) {
        // 离开大厅
        lobbyService.leaveLobby(roomId , sessionId);
    }
}
