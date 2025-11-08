package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.ChatGroupMemberListVO;
import com.guanyu.haigui.pojo.vo.ChatGroupVo;
import com.guanyu.haigui.pojo.vo.GroupMessageVO;
import com.guanyu.haigui.pojo.vo.GroupRoomListVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.websocket.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "群聊接口", description = "群聊相关接口")
@Slf4j
@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class ChatGroupController {
    private final GroupService groupService;

    /**
     * 创建游戏房间
     *
     * @param request   创建房间请求参数
     */
    @Operation(summary = "创建群聊")
    @ResponseBody
    @PostMapping("/createGroupRoom")
    public Result<String> createGroupRoom(@RequestBody CreateGroupRequest request) {
        // 创建群聊并获取生成的房间ID
        return Result.success(groupService.createGroupRoom(request));
    }


    /**
     * 上传用户头像接口
     * @param avatarFile 头像文件（表单参数，name=avatar）
     * @return 头像访问URL或错误信息
     */
    @Operation(summary = "上传群头像")
    @PostMapping("/upGroupAvatar/{groupId}")
    public Result<?> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile,@PathVariable String groupId) {
        if (avatarFile.isEmpty()) {
            return Result.error("文件不能为空");
        }
        return Result.success(groupService.uploadGroupAvatar(avatarFile,groupId));
    }

    @Operation(summary = "修改群名称")
    @PostMapping("/upGroupName")
    public Result<?> updateGroupName(@RequestBody updateGroupNameDTO request) {
        return Result.success(groupService.updateGroupName(request));
    }


    /**
     * 获取自己已经加入的群聊
     */
    @Operation(summary = "搜索自己已经加入的群聊")
    @GetMapping("/mineGroupRoom")
    @ResponseBody
    public Result<List<ChatGroupVo>> getMineLobbies() {
        // 获取当前用户创建的群聊
        return Result.success(groupService.getMineGroupRooms());
    }



    @Operation(summary = "搜索群聊")
    @PostMapping("/searchLobbies")
    @ResponseBody
    public PageImpl<GroupRoomListVO> searchLobbies(@RequestBody SearchChatGroupMessage message) {
        // 解析参数
        CharGroupListDTO dto = message.getDto();
        int page = message.getPage();

        // 调用原分页查询方法
        return groupService.searchLobbies(dto, page);
    }


    // 获取指定房间的历史消息
    @Operation(summary = "获取指定群聊的历史消息")
    @PostMapping("/chat.history")
    @ResponseBody
    public Page<GroupMessageVO> getRoomChatHistory(@RequestBody GroupChatHistoryDTO groupChatHistoryDTO) {
        return groupService.getGroupMessages(groupChatHistoryDTO);
    }



    @Operation(summary = "用户申请加入群聊")
    @PostMapping("/joinGroupRoom")
    public GroupJoinNotification joinRoom(@RequestBody JoinGroupRoomRequest request) {
        // 加入群聊
        return groupService.applyJoinGroup(request);
    }

    @Operation(summary = "群主同意用户加入群聊的请求")
    @PostMapping("/AgreeJoinGroupRoom")
    public void agreeJoinRoom(@RequestBody dealJoinGroupRoomRequest request) {
        // 加入群聊
        groupService.agreeJoinRequest(request);
    }

    @Operation(summary = "群主拒绝用户加入群聊的请求")
    @PostMapping("/RefuseJoinGroupRoom")
    public void refuseJoinRoom(@RequestBody dealJoinGroupRoomRequest request) {
        // 加入群聊
        groupService.RefuseJoinRequest(request);
    }



    @Operation(summary = "获取指定群聊的最新N条消息")
    @PostMapping("/chat.recent/{groupId}/{limit}") // 接收请求：包含roomId和limit
    @SendTo("/topic/recent/{groupId}") // 广播结果：发送到对应房间的Topic（仅订阅该房间的客户端能收到）
    public List<GroupMessageVO> getRecentMessages(
            @PathVariable String groupId, // 从路径变量获取roomId
            @PathVariable int limit) { // 从路径变量获取limit
        return groupService.getRecentMessages(groupId, limit);
    }


    // 处理发送聊天消息的请求（前端发送到/app/chat.sendMessage）
    @Operation(summary = "处理发送聊天消息的请求")
    @PostMapping("/sendGroupRoomMessage")
    public GroupMessageVO sendGroupRoomMessage(@RequestBody SendGroupMessageRequest message) {
        return groupService.sendGroupRoomMessage(message);
    }


    @Operation(summary = "退出群聊")
    @PostMapping("/leaveGroupRoom/{groupId}")
    public void leaveGroupRoom(@PathVariable String groupId) {
        // 获取当前登录用户ID（从SecurityContext）
        groupService.leaveGroupRoom(groupId);
    }

    @Operation(summary = "获取指定群聊成员（分页）")
    @GetMapping("/getGroupUsers/{groupId}")
    public Result<ChatGroupMemberListVO> getGroupUsers(
            @PathVariable String groupId,
            // 接收分页参数，设置默认值
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        // 注意：Spring Data JPA的PageRequest页码从0开始，所以page-1
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        // 调用Service获取分页结果
        ChatGroupMemberListVO result = groupService.getGroupUsers(groupId, pageable);
        System.out.println(result);
        return Result.success(result);
    }

}
