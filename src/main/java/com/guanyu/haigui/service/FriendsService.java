package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FriendsService {
    Page<FriendSearchResultVO> searchFriends(String keyword, Pageable pageable);

    FriendRetractNotificationVO sendFriendApply(Long currentUserId, Long targetUserId, String remark);

    void acceptFriendApply(Long applicationId, Long currentUserId);

    void rejectFriendApply(Long applicationId, Long currentUserId);

    void deleteFriend(Long currentUserId, Long friendId);

    Boolean isFriend(Long currentUserId, Long friendId);

    List<FriendSearchListVO> getFriendListWithMessages();

    List<FriendApplicationVO> getReceivedApplications();

    List<FriendApplicationVO> getSentApplications();

    FriendRetractNotificationVO retractFriendApply(Long applicationId, Long currentId);
}
