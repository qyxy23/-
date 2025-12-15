package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import lombok.Data;

@Data
public class HaiGuiInfoGenerateDTO {
    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 预计游玩时间（分钟）
    private Integer estimatedDuration = 30;

    // 游玩人数限制，0表示不限制，最多10人
    private Integer playerCount = 0;

    //难度
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 时间限制
    private String timeLimit;

    // 最大轮数
    private String maxRounds;

    // 标签
    private SoupTag tag;

}
