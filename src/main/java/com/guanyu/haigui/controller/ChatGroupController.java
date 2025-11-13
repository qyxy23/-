package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.*;
import com.guanyu.haigui.pojo.vo.*;
import com.guanyu.haigui.repository.ChatGroupAdminRepository;
import com.guanyu.haigui.repository.ChatGroupRepository;
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
    private final ChatGroupAdminRepository chatGroupAdminRepository;
    private final ChatGroupRepository chatGroupRepository;

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

    @Operation(summary = "用户撤回申请加入群聊的请求")
    @PostMapping("/retractJoinGroupRoom")
    public GroupApplyRetractNotificationVO retractJoinRoom(@RequestBody dealJoinGroupRoomRequest request) {
        // 获取当前登录用户ID（从SecurityContext）
        return groupService.retractGroupJoinApplyById(request.getRequestId(), BaseContext.getCurrentId());
    }

    @Operation(summary = "群主/管理员同意用户加入群聊的请求")
    @PostMapping("/AgreeJoinGroupRoom")
    public Result<String> agreeJoinRoom(@RequestBody dealJoinGroupRoomRequest request) {
        // 加入群聊
        groupService.agreeJoinRequest(request);
        return Result.success("申请已通过");
    }

    @Operation(summary = "群主/管理员拒绝用户加入群聊的请求")
    @PostMapping("/RefuseJoinGroupRoom")
    public Result<String> refuseJoinRoom(@RequestBody dealJoinGroupRoomRequest request) {
        // 加入群聊
        groupService.RefuseJoinRequest(request);
        return Result.success("申请已拒绝");
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

    @Operation(summary = "获取入群申请（分页）")
    @GetMapping("/getGroupJoinRequests")
    public Result<ChatGroupJoinRequestListVO> getGroupJoinRequests(
            // 获取分页参数，设置默认值
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        // 注意：Spring Data JPA的PageRequest页码从0开始，所以page-1
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        // 调用Service获取分页结果
        ChatGroupJoinRequestListVO result = groupService.getGroupJoinRequests(pageable);
        System.out.println(result);
        return Result.success(result);
    }


    // ===================== 增加普通管理员 =====================
    @Operation(summary = "群主添加普通管理员")
    @PostMapping("/add")
    public Result<String> addAdministrator(
            @RequestParam String groupId,          // 群ID
            @RequestParam Long adminUserId){        // 要添加的管理员用户ID
        groupService.addAdministrator(groupId, adminUserId);
        return Result.success("添加成功");
    }

    // ===================== 删除普通管理员 =====================
    @Operation(summary = "群主删除普通管理员")
    @PostMapping("/remove")
    public Result<String> removeAdministrator(
            @RequestParam String groupId,          // 群ID
            @RequestParam Long adminUserId){        // 要删除的管理员用户ID
        groupService.removeAdministrator(groupId, adminUserId);
        return Result.success("删除成功");
    }

    // GroupController.java
    @GetMapping("/{groupId}/detail")
    @Operation(summary = "获取群详情（含当前用户权限）")
    public Result<GroupDetailVO> getGroupDetail(
            @PathVariable String groupId) {
        return Result.success(groupService.getGroupDetail(groupId));
    }

    @Operation(summary = "查询自己已经发送的群申请(分页)")
    @GetMapping("/getGroupPermission")
    public Result<MineChatGroupJoinRequestListVO> getGroupPermission(
            @RequestParam(defaultValue = "1") Integer page,       // 前端页码（从1开始）
            @RequestParam(defaultValue = "10") Integer pageSize){  // 每页数量

        // 构造Spring Data JPA的分页参数（页码从0开始）
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        // 调用Service获取分页结果
        MineChatGroupJoinRequestListVO result = groupService.getGroupPermission(pageable);

        return Result.success(result);
    }
}
