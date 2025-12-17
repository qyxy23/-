package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.InferenceTask;
import lombok.Data;

import java.util.List;

@Data
public class UpdateHaiGuiAuditDTO {
    // 审核id
    private Long auditId;

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

    // 难度等级
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    //默认最大询问次数
    private Integer DefaultMaxQuestions = 20;

    // 海龟汤标签（只能选择一个）
    private SoupTag tag;

    //主持人手册
    private String draftManual;

    // 汤线索
    private List<ClueFragment> draftFragments;

    // 汤任务
    private List<InferenceTask> draftTasks;
}
