package com.guanyu.haigui.pojo.response;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class AIResponse {
    private String rawResponse;
    /** 是 / 不是 / 是或不是 / 不重要 / 无效提问 */
    private String answer;
    private String reason;
    /** AI 裁决触发的线索 ID（需在 Java 侧与候选集交叉校验） */
    private Set<Long> triggeredFragmentIds = new LinkedHashSet<>();
}
