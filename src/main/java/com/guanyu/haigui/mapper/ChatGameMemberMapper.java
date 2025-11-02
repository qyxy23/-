package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.vo.MemberSimpleVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

public interface ChatGameMemberMapper {
    void joinRoomMember(ChatGameMember member);
    boolean existsByRoomIdAndMemberId(String roomId, Long userId);
    void addMember(ChatGameMember member);
    List<MemberSimpleVO> selectMembersByRoomIds(@Param("roomIds") Set<String> roomIds);
}