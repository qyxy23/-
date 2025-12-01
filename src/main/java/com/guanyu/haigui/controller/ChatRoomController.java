package com.guanyu.haigui.controller;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.websocket.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "大厅聊天接口", description = "大厅相关接口")
@Slf4j
@AllArgsConstructor
@Controller
public class ChatRoomController {
    private final RoomService roomService;

    /**
     * 创建游戏房间
     *
     * @param request 创建房间请求参数
     */
    @Operation(summary = "创建大厅")
    @ResponseBody
    @PostMapping("/createLobby")
    public Result<String> createLobby(@RequestBody CreateRoomRequest request) {
        // 创建大厅并获取生成的房间ID
        return Result.success(roomService.createChatRoom(request));
    }

    /**
     * 获取自己已经加入的大厅
     */
    @Operation(summary = "搜索自己已经加入的大厅")
    @GetMapping("/mineLobby")
    @ResponseBody
    public Result<List<ChatGameDTO>> getMineLobbies() {
        // 获取当前用户创建的大厅
        return Result.success(roomService.getMineLobbies());
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
        return roomService.searchLobbies(dto, page);
    }

    // 获取指定房间的历史消息
    @Operation(summary = "获取指定房间的历史消息")
    @PostMapping("/chat.history")
    @ResponseBody
    public Page<GameRoomMessageVO> getRoomChatHistory(@RequestBody RoomChatHistoryDTO roomChatHistoryDTO) {
        return roomService.getGameMessages(roomChatHistoryDTO);
    }

    @Operation(summary = "邀请某人加入房间")
    @PostMapping("/invite")
    @ResponseBody
    public Result<InvitationVO> invite(@RequestBody InvitationDto request) {
        return Result.success(roomService.invite(request));
    }

    // 处理用户加入大厅的请求（前端发送到/app/chat.joinLobby）
    @Operation(summary = "加入大厅")
    @PostMapping("/joinRoom")
    @ResponseBody
    public Result<joinChatRoomVO> joinRoom(@RequestBody JoinChatRoomRequest request) {
        // 加入大厅
        return Result.success(roomService.joinChatRoom(request.getChatRoomId()));
    }

    @Operation(summary = "离开大厅")
    @PostMapping("/leaveLobby/{roomId}")
    @ResponseBody
    public Result<leaveLobbyVO> leaveLobby(@PathVariable String roomId) {
        // 离开大厅
        return Result.success(roomService.leaveLobby(roomId));
    }

    @Operation(summary = "获取指定房间的最新N条消息")
    @MessageMapping("/chat.recent/{roomId}/{limit}") // 接收请求：包含roomId和limit
    @SendTo("/topic/recent/{roomId}") // 广播结果：发送到对应房间的Topic（仅订阅该房间的客户端能收到）
    public List<GameRoomMessageVO> getRecentMessages(
            @DestinationVariable String roomId, // 从路径变量获取roomId
            @DestinationVariable int limit) { // 从路径变量获取limit（带校验）
        return roomService.getRecentMessages(roomId, limit);
    }


    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @MessageMapping("/ws/sendLobbyMessage")
    public void sendLobbyMessage(@Payload SendGameRoomMsgRequest message, @Header("simpSessionId") String sessionId) {
        roomService.sendLobbyMessage(message, sessionId);
    }


    /**
     * 查询游戏大厅的所有成员
     * @param roomId 房间ID
     * @return 统一响应（包含成员列表）
     */
    @GetMapping("/members/{roomId}")
    @ResponseBody
    @Operation(summary = "查询游戏大厅的所有成员")
    public Result<searchAllLobbyMemberVO> searchAllLobbyMembers(@PathVariable String roomId) {
        try {
            // 调用Service获取成员列表
            searchAllLobbyMemberVO members = roomService.getAllMembersByRoomId(roomId);
            // 返回成功响应（200 OK）
            return Result.success(members);
        } catch (BusinessException e) {
            // 业务异常（如房间不存在）：返回404
            throw new BusinessException(404, e.getMessage());
        } catch (Exception e) {
            // 系统异常：返回500
            return Result.error("系统繁忙，请稍后重试");
        }
    }



    @Operation(summary = "挂起房间")
    @PostMapping("/suspendRoom/{roomId}")
    @ResponseBody
    public Result<suspendRoomVO> suspendRoom(@PathVariable String roomId) {
        return Result.success(roomService.suspendRoom(roomId));
    }

    @Operation(summary = "返回房间")
    @PostMapping("/returnRoom/{roomId}")
    @ResponseBody
    public Result<resumeRoomVO> returnRoom(@PathVariable String roomId) {
        return Result.success(roomService.returnRoom(roomId));
    }


    @Operation(summary = "游戏准备")
    @PostMapping("/ready/{roomId}")
    @ResponseBody
    public Result<readyVO> ready(@PathVariable String roomId) {
        return Result.success(roomService.ready(roomId));
    }

    // 处理大厅人数是否达标，若达到要求可开始游戏
    @Operation(summary = "处理大厅人数是否达标且都准备完毕，若达到要求可开始游戏")
    @PostMapping("/checkRoomStatus")
    public Result<checkRoomStatusVO> checkRoomStatus(@Payload String roomId) {
        log.info("检查房间{}的状态", roomId);
        return Result.success(roomService.checkRoomStatus(roomId));
    }

}