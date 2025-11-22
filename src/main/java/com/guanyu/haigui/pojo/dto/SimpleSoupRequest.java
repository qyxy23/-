package com.guanyu.haigui.pojo.dto;

import lombok.Data;

/**
 * 简化海龟汤请求类
 */
@Data
public class SimpleSoupRequest {
    private String soupId;
    private String soupTitle;
    private String soupSurface;
    private String soupBottom;
    private String hostManual;
    private Integer playCount;
    private java.util.Date createdAt;
}