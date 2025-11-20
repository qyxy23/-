package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class MemberSimpleVO {
    // 房间ID
    @JsonIgnore
    private String roomId;
    // 用户ID（可选，根据需求保留）
    private Long userId;
    // 头像
    private String avatar;
    // 用户名（可选，根据需求保留）
    private String username;
    // 其他需要的字段（如手机号、邮箱等，按需添加）
    // private String phone;
}