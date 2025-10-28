package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatRoom;
import lombok.Data;

// 创建ChatRoomDTO类
@Data
public class ChatRoomDTO {
    private String roomId;
    private String roomName;
    private RoomStatus status;
    private Integer requiredMembers;
    private Integer currentMembers;
    private UserInfoDTO creator;

    // getters and setters

    public static ChatRoomDTO from(ChatRoom chatRoom) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setRoomId(chatRoom.getRoomId());
        dto.setRoomName(chatRoom.getRoomName());
        dto.setRequiredMembers(chatRoom.getRequiredMembers());
        dto.setCurrentMembers(chatRoom.getCurrentMembers());
        dto.setStatus(chatRoom.getStatus());

        // 手动获取creator信息，避免懒加载问题
        if (chatRoom.getCreator() != null) {
            UserInfoDTO creatorDTO = new UserInfoDTO();
            creatorDTO.setUserId(chatRoom.getCreator().getUserId());
            creatorDTO.setUsername(chatRoom.getCreator().getUsername());
            dto.setCreator(creatorDTO);
        }

        return dto;
    }
}