package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.dto.ChatGameDTO;
import lombok.Data;

import java.util.List;

/** 「我的大厅」聚合：已加入的多人大厅 + 进行中的单人游戏 */
@Data
public class MyLobbyHubVO {
    private List<ChatGameDTO> lobbies;
    private List<OngoingSoloVO> ongoingSoloGames;
}
