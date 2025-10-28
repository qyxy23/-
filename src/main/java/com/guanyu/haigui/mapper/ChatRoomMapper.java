package com.guanyu.haigui.mapper;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.dto.LobbyListDTO;
import com.guanyu.haigui.pojo.model.ChatRoom;
import com.guanyu.haigui.pojo.vo.LobbyListVO;
import com.guanyu.haigui.pojo.vo.MemberSimpleVO;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

public interface ChatRoomMapper {

    Integer createChatRoom(ChatRoom room);

    ChatRoom checkByRoomIdAndStatus(String roomId, RoomStatus roomStatus);

    void updateRoom(ChatRoom room);

    void updateRoomStatus(ChatRoom room);

    ChatRoom checkByRoomId(String roomId);

    /**
     * 动态查询聊天室（返回LobbyListVO，含关联对象）
     * @param dto 查询参数（可选字段）
     * @return 符合条件的聊天室VO列表（PageHelper会自动分页）
     */
    List<LobbyListVO> searchLobbies(LobbyListDTO dto);

    // ChatRoomMemberMapper.java
    List<MemberSimpleVO> selectSimpleMembersByRoomId(String roomId);

    // ChatRoomMapper.java
    @MapKey("roomId")
    List<Map<String, Object>> searchLobbiesBase(LobbyListDTO dto);
}
