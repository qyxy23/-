package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.FriendInfoVO;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;

import java.util.List;

public interface FriendsService {
    List<FriendSearchResultVO> searchFriends(String keyword);

    void sendFriendApply(Long currentUserId, Long targetUserId, String remark);

    void acceptFriendApply(Long applicationId, Long currentUserId);

    void deleteFriend(Long currentUserId, Long friendId);

    FriendInfoVO getFriendInfo(Long currentUserId, Long friendId);

    List<FriendSearchListVO> searchFriendsList();
}
