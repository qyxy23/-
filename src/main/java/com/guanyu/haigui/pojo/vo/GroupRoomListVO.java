package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Builder
public class GroupRoomListVO {
    private String groupId;

    // 群聊名称
    private String groupName;

    private String creatorName;

    private Long memberCount;

    private LocalDateTime createTime;
}
