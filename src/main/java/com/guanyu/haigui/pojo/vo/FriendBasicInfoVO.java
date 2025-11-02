package com.guanyu.haigui.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendBasicInfoVO {
    private Long userId;
    private String username;
    private String avatar;
}