package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatGame;
import lombok.Data;

// 创建ChatRoomDTO类
@Data
public class ChatGameDTO {
    private String roomId;
    private String roomName;
    private RoomStatus status;
    private Integer requiredMembers;
    private Integer currentMembers;
    private UserInfoDTO creator;

    // getters and setters

    public static ChatGameDTO from(ChatGame chatGame) {
        ChatGameDTO dto = new ChatGameDTO();
        dto.setRoomId(chatGame.getRoomId());
        dto.setRoomName(chatGame.getRoomName());
        dto.setRequiredMembers(chatGame.getRequiredMembers());
        dto.setCurrentMembers(chatGame.getCurrentMembers());
        dto.setStatus(chatGame.getStatus());

        // 手动获取creator信息，避免懒加载问题
        if (chatGame.getCreator() != null) {
            UserInfoDTO creatorDTO = new UserInfoDTO();
            creatorDTO.setUserId(chatGame.getCreator().getUserId());
            creatorDTO.setUsername(chatGame.getCreator().getUsername());
            dto.setCreator(creatorDTO);
        }

        return dto;
    }
}