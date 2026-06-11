package com.guanyu.haigui.pojo.dto;

import lombok.Data;

@Data
public class ReviewPlayAccessRequestDTO {
    private Long requestId;
    /** true=通过 false=拒绝 */
    private Boolean approved;
    private String adminNote;
    /** 通过时赠送局数，空则使用系统默认 */
    private Integer grantedGames;
}
