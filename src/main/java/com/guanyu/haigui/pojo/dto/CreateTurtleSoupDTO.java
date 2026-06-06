package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.UserInfo;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateTurtleSoupDTO {
    // 审核记录的id
    private Long auditRecordId;

    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 主持人手册（供真人主持）
    private String manual;

    // AI 判题规则
    private String aiJudgeRules;

    //线索
    private List<ClueFragmentInfo> fragments;

    //推理任务
    private List<InferenceTaskInfo> inferenceTasks;

    // 预计游玩时间（分钟）
    private Integer estimatedDuration = 30;

    // 游玩人数限制，0表示不限制，最多10人
    private Integer playerCount = 0;

    // 难度等级
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;

    // 海龟汤标签（只能选择一个）
    @NotNull
    private SoupTag tag;

    //最大问题数
    private Integer maxRounds = 10;

    public HaiGuiSoup fromToHaiGuiSoup(UserInfo userInfo) {
        HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
        haiGuiSoup.setSoupId(UUID.randomUUID().toString());
        haiGuiSoup.setPlayCount(0);
        haiGuiSoup.setUploadTime(LocalDateTime.now());
        haiGuiSoup.setCreatedAt(LocalDateTime.now());
        haiGuiSoup.setUpdatedAt(LocalDateTime.now());
        haiGuiSoup.setSoupTitle(soupTitle);
        haiGuiSoup.setSoupSurface(soupSurface);
        haiGuiSoup.setSoupBottom(soupBottom);
        haiGuiSoup.setHostManual(manual);
        haiGuiSoup.setAiJudgeRules(aiJudgeRules != null ? aiJudgeRules : "");
        haiGuiSoup.setDefaultMaxQuestions(maxRounds);
        haiGuiSoup.setTaskGenerationStrategy("HYBRID");
        haiGuiSoup.setVectorMatchThreshold(new BigDecimal("0.7"));
        haiGuiSoup.setDifficultyLevel(difficultyLevel);
        haiGuiSoup.setTags(tag);
        haiGuiSoup.setEstimatedDuration(estimatedDuration);
        haiGuiSoup.setPlayerCount(playerCount);
        haiGuiSoup.setKeyClues("[]");
        haiGuiSoup.setUploaderId(userInfo.getUserId());
        haiGuiSoup.setCreatorId(userInfo.getUserId());
        haiGuiSoup.setUploader(userInfo);
        haiGuiSoup.setCreator(userInfo);
        haiGuiSoup.setIsPublished(true);
        return haiGuiSoup;
    }
}
