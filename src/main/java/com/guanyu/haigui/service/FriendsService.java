package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.FriendApplicationVO;
import com.guanyu.haigui.pojo.vo.FriendInfoVO;
import com.guanyu.haigui.pojo.vo.FriendSearchListVO;
import com.guanyu.haigui.pojo.vo.FriendSearchResultVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FriendsService {
    Page<FriendSearchResultVO> searchFriends(String keyword, Pageable pageable);

    void sendFriendApply(Long currentUserId, Long targetUserId, String remark);

    void acceptFriendApply(Long applicationId, Long currentUserId);

    void deleteFriend(Long currentUserId, Long friendId);

    FriendInfoVO getFriendInfo(Long currentUserId, Long friendId);

    List<FriendSearchListVO> getFriendListWithMessages();


    List<FriendApplicationVO> getReceivedApplications();

    List<FriendApplicationVO> getSentApplications();
}
