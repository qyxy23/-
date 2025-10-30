package com.guanyu.haigui.controller;

import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.FriendApplyRequest;
import com.guanyu.haigui.pojo.vo.FriendInfoVO;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.FriendsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@Tag(name = "好友接口", description = "好友相关接口")
@RequestMapping("/friends")
public class FriendsController {
    private final FriendsService friendService;



    /**
     * 搜索可添加的好友
     */
    @GetMapping("/search")
    @Operation(summary = "搜索可添加的好友")
    public Result<List<FriendSearchResultVO>> searchFriends(@RequestParam String keyword) {
        List<FriendSearchResultVO> result = friendService.searchFriends(keyword);
        return Result.success(result);
    }

    /**
     * 获取好友列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取好友列表")
    public Result<List<FriendSearchListVO>> FriendsList() {
        List<FriendSearchListVO> result = friendService.searchFriendsList();
        return Result.success(result);
    }



    /**
     * 发送好友申请
     */
    @PostMapping("/apply")
    @Operation(summary = "发送好友申请")
    public Result<String> sendFriendApply(@RequestBody FriendApplyRequest request) {
        friendService.sendFriendApply(BaseContext.getCurrentId(), request.getTargetUserId(), request.getRemark());
        return Result.success("申请已发送");
    }


    /**
     * 同意好友申请
     */
    @PostMapping("/accept/{applicationId}")
    @Operation(summary = "同意好友申请")
    public Result<String> acceptFriendApply(@PathVariable Long applicationId) {
        friendService.acceptFriendApply(applicationId,BaseContext.getCurrentId());
        return Result.success("申请已同意");
    }


    /**
     * 删除好友
     */
    @DeleteMapping("/{friendId}")
    @Operation(summary = "删除好友")
    public Result<String> deleteFriend(@PathVariable Long friendId) {
        friendService.deleteFriend(BaseContext.getCurrentId(), friendId);
        return Result.success("好友已删除");
    }


    /**
     * 获取好友信息
     */
    @GetMapping("/{friendId}")
    @Operation(summary = "获取好友信息")
    public Result<FriendInfoVO> getFriendInfo(@PathVariable Long friendId) {
        FriendInfoVO result = friendService.getFriendInfo(BaseContext.getCurrentId(), friendId);
        return Result.success(result);
    }
}

