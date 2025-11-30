package com.guanyu.haigui.pojo.dto;

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
     * 汤底
     */
    private String soupBottom;

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
}