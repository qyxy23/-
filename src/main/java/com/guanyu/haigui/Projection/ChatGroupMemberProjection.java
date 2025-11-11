package com.guanyu.haigui.Projection;

import java.time.LocalDateTime;

public interface ChatGroupMemberProjection {
    /** 成员ID（对应sys_user.user_id） */
    Long getMemberId();
    /** 群ID（对应chat_groups.group_id） */
    String getGroupId();
    /** 加入时间 */
    LocalDateTime getJoinTime();
    // 可按需添加其他字段（如member的username、avatar等）
}