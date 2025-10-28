package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.ChatRoomMember;
import com.guanyu.haigui.pojo.vo.MemberSimpleVO;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ChatRoomMemberMapper {

    void joinRoomMember(ChatRoomMember member);

    boolean existsByRoomIdAndMemberId(String roomId, Long userId);

    void addMember(ChatRoomMember member);


    // 修复后的注解位置
    List<MemberSimpleVO> selectMembersByRoomIds(@Param("roomIds") Set<String> roomIds);

}
