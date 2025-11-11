package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.GroupRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
@AllArgsConstructor
@Data
public class ChatGroupMemberVO {
    private Long memberId;
    private String memberName;
    private String memberAvatar;
    private GroupRoleEnum groupRoleEnum;
}