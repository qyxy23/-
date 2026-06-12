package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 对局内成员 AI 提问贡献（多人进行中） */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberContributionView {

    private Long userId;

    private String username;

    private String avatar;

    /** 向 AI 提问次数 */
    private Integer questionCount;

    /** AI 判「是」的次数 */
    private Integer yesCount;

    /** 首次触发线索数（与复盘 MVP 一致） */
    private Integer triggeredClueCount;

    /** 当前是否并列 MVP（按触发线索数） */
    private Boolean mvp;
}
