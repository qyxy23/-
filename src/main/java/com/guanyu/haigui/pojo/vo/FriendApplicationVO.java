package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.FriendStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendApplicationVO {
    /** 申请记录ID（对应friend_relations.id） */
    private Long applicationId;
    /** 申请人ID（主动发送申请的用户） */
    private Long applicantId;
    /** 申请人昵称 */
    private String applicantName;
    /** 申请人头像 */
    private String applicantAvatar;
    /** 申请备注（主动方填写的内容） */
    private String remark;
    /** 申请状态 */
    private FriendStatus status;
    /** 申请时间 */
    private LocalDateTime createTime;
}