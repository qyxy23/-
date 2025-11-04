package com.guanyu.haigui.mapper;

public interface SessionStickyMapper {


    void updateGroupSticky(Long currentUserId, String sessionId, boolean isSticky);

    void updatePrivateSticky(Long currentUserId, String sessionId, boolean isSticky);

    boolean selectPrivateSticky(Long currentUserId, String sessionId);
}
