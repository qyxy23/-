package com.guanyu.haigui.pojo.result;

import lombok.Data;

@Data
public class GameHistoryMemberView {
    private Long userId;
    private String username;
    private String avatar;
    /** 首次触发线索数（贡献分） */
    private int score;
    private int questionCount;
    /** AI 判「是」的次数 */
    private int yesCount;
    private boolean mvp;
}
