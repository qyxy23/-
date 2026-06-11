package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.ReplayBuildHints;
import com.guanyu.haigui.pojo.vo.EndGameVO;
import com.guanyu.haigui.pojo.vo.getAIChatListDetailVO;

public interface GameReplayService {

    getAIChatListDetailVO getDetailForUser(String roomId, String gameSessionId, Long userId);

    /** 对局结束时构建复盘并写入缓存，同时挂到 EndGameVO */
    void attachReplayAtEnd(EndGameVO endGameVO, String gameSessionId, String roomId, Long soloUserId);

    /** 对局结束时构建复盘（可传入已算好的 settlement 快照，避免重复 Builder） */
    void attachReplayAtEnd(EndGameVO endGameVO, String gameSessionId, String roomId, Long soloUserId,
                           ReplayBuildHints hints);

    /** 结算页补拉：优先读缓存，miss 时再构建并写入 */
    getAIChatListDetailVO getOrBuildAndCache(String gameSessionId, String roomId, Long soloUserId);

    /** 结算页补拉（可传入 settlement 快照） */
    getAIChatListDetailVO getOrBuildAndCache(String gameSessionId, String roomId, Long soloUserId,
                                             ReplayBuildHints hints);
}
