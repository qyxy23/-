package com.guanyu.haigui.pojo.result;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GameHistoryTimelineItem {
    /** LOBBY 大厅聊天 / AI_QUESTION AI 提问 */
    private String type;
    private LocalDateTime time;

    private Long userId;
    private String username;
    private String avatar;
    private String content;
    private String answer;
    private List<ClueSummaryView> triggeredClues = new ArrayList<>();
}
