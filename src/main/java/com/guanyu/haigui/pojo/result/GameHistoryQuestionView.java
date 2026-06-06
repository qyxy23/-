package com.guanyu.haigui.pojo.result;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GameHistoryQuestionView {
    private Long userId;
    private String username;
    private String avatar;
    private String question;
    private String answer;
    private LocalDateTime sendTime;
    private List<ClueSummaryView> triggeredClues = new ArrayList<>();
}
