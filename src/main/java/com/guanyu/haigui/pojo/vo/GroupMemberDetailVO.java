// package com.guanyu.haigui.pojo.vo;
//
// import com.guanyu.haigui.Enum.GroupRoleEnum;
// import com.guanyu.haigui.pojo.model.ChatGroupMember;
// import lombok.Data;
// import java.time.LocalDateTime;
//
// @Data
// public class GroupMemberDetailVO {
//     /** 成员ID */
//     private Long memberId;
//     /** 群内昵称 */
//     private String nickname;
//     /** 角色（0=成员/1=管理员/2=群主） */
//     private GroupRoleEnum role;
//     private GroupMemberDetailVO convertToVO(ChatGroupMember member) {
//         GroupMemberDetailVO vo = new GroupMemberDetailVO();
//         vo.setMemberId(member.getId().getMemberId());
//         vo.setNickname(member.getNickname());
//         vo.setRole(member.());
//         return vo;
//     }
// }