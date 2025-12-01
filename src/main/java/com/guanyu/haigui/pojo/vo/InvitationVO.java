package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.model.ChatGameInvitation;
import lombok.Data;


@Data
public class InvitationVO {
    /** 邀请唯一ID */
    private String invitationId;
    /** 房间名称（方便被邀请者识别） */
    private String roomName;
    /** 邀请者昵称（展示给被邀请者） */
    private String inviterName;
    /** 邀请状态 */
    private String status;       // 对应InvitationStatus的字符串值（如"PENDING"）

    /**
     * 从实体转换到VO（静态方法，方便调用）
     */
    public static InvitationVO fromEntity(ChatGameInvitation invitation) {
        InvitationVO vo = new InvitationVO();
        vo.setInvitationId(invitation.getInvitationId());
        vo.setRoomName(invitation.getChatGame().getRoomName());
        vo.setInviterName(invitation.getInviter().getUsername());
        vo.setStatus(invitation.getStatus().name());
        return vo;
    }
}
