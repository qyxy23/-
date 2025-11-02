package com.guanyu.haigui.mapper;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.dto.LobbyListDTO;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.vo.LobbyListVO;
import com.guanyu.haigui.pojo.vo.MemberSimpleVO;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

public interface ChatGameMapper {
    Integer createChatGame(ChatGame room);
    ChatGame checkByRoomIdAndStatus(String roomId, RoomStatus roomStatus);
    void updateRoom(ChatGame room);
    void updateRoomStatus(ChatGame room);
    ChatGame checkByRoomId(String roomId);
    List<LobbyListVO> searchLobbies(LobbyListDTO dto);
    List<MemberSimpleVO> selectSimpleMembersByRoomId(String roomId);

    @MapKey("roomId")
    List<Map<String, Object>> searchLobbiesBase(LobbyListDTO dto);
}