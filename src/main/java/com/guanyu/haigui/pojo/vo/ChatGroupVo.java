package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupVo {
    private String groupId;       // 群ID
    private String groupName;     // 群名称
    private String groupAvatar;   // 群头像
    private Long memberCount;       // 群成员数
    private LocalDateTime createTime;// 群创建时间
}