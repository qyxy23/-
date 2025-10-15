package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.ChatRoomMember;

public interface ChatRoomMemberMapper {

    void joinRoomMember(ChatRoomMember member);

    boolean existsByRoomIdAndMemberId(String roomId, Long userId);

    void addMember(ChatRoomMember member);
}
