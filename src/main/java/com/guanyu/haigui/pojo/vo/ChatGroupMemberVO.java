package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
@AllArgsConstructor
@Data
public class ChatGroupMemberVO {
    private Long memberId;
    private String memberName;
    private String memberAvatar;
}