package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;
@Data
public class searchAllLobbyMemberVO {
    private String memberId;
    private Integer memberNum;
    private Integer maxMembers;
    private List<LobbyMemberVO> memberList;
}
