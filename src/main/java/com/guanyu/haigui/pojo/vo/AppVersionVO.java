package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class AppVersionVO {

    private boolean needUpdate;

    private boolean forceUpdate;

    private Integer versionCode;

    private String versionName;

    /** wgt | apk */
    private String updateType;

    private String downloadUrl;

    private String changelog;

    private Long fileSize;

    private String fileMd5;
}
