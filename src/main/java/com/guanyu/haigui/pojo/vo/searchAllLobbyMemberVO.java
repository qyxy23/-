package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;
@Data
public class searchAllLobbyMemberVO {
    private String memberId;
    private String memberName;
    private Integer memberNum;
    private List<LobbyMemberVO> memberList;
}
