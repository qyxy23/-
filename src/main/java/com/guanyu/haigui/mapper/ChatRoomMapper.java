package com.guanyu.haigui.mapper;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatRoom;

public interface ChatRoomMapper {

    ChatRoom createChatRoom(ChatRoom room);

    ChatRoom checkByRoomIdAndStatus(String roomId, RoomStatus roomStatus);

    void updateRoom(ChatRoom room);

    void updateRoomStatus(ChatRoom room);

    ChatRoom checkByRoomId(String roomId);
}
