package com.guanyu.haigui.pojo.dto;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 海龟汤投影DTO，用于JPA查询结果映射
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoupProjectionDTO {
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private Integer playCount;
    private Long uploaderId;
    private String uploaderAvatar;
    private LocalDateTime uploadTime;
    private Integer estimatedDuration;
    private Integer playerCount;
    private DifficultyLevel difficultyLevel;
    private SoupTag tag; // 使用新枚举类型
    private String soupAvatar;
}