package com.guanyu.haigui.Projection;

import com.guanyu.haigui.pojo.model.ChatGroupAdministrator;
import com.guanyu.haigui.pojo.model.ChatGroupMember;

public interface ChatGroupMemberWithRole {
    ChatGroupMember getMember();          // 群成员基础信息（含关联的UserInfo）
    ChatGroupAdministrator getAdministrator(); // 关联的管理员记录（可能为null）
}