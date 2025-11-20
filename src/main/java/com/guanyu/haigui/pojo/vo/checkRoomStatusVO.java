package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.RoomStatus;
import lombok.Data;

@Data
public class checkRoomStatusVO {
    private String roomId;
    private RoomStatus status;
}
