package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CoverAiGenStatusVO {
    private boolean generating;
    /** 正在生成的审核员 userId（generating 为 true 时有值） */
    private Long generatingUserId;
    /** 是否为当前登录用户发起 */
    private boolean generatingByMe;
}
