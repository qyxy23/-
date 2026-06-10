package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.OngoingSoloVO;
import com.guanyu.haigui.pojo.vo.RoomGetClueVO;
import com.guanyu.haigui.pojo.vo.StartSoloVO;

import java.util.List;

public interface SoloGameService {

    StartSoloVO startSolo(String soupId);

    List<OngoingSoloVO> listOngoing();

    RoomGetClueVO getSoloState(String gameSessionId);

    EndGameVO giveUp(String gameSessionId);

    EndGameVO getSettlement(String gameSessionId);
}
