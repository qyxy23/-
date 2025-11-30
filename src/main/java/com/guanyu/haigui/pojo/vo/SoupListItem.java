package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.Enum.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 海龟汤列表项VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoupListItem {
    /**
     * 海龟汤ID
     */
    private String soupId;

    /**
     * 标题
     */
    private String soupTitle;

    /**
     * 汤面
     */
    private String soupSurface;


    /**
     * 游玩次数
     */
    private Integer playCount;

    /**
     * 上传者ID
     */
    private Long uploaderId;

    /**
     * 上传者头像
     */
    private String uploaderAvatar;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 预计游玩时间（分钟）
     */
    private Integer estimatedDuration;

    /**
     * 游玩人数限制，0表示不限制，最多10人
     */
    private Integer playerCount;

    /**
     * 难度等级
     */
    private DifficultyLevel difficultyLevel;

    /**
     * 海龟汤标签（只能选择一个）
     */
    private String tag;
}