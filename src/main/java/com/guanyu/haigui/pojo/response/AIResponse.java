package com.guanyu.haigui.pojo.response;

import lombok.Data;

@Data
public class AIResponse {
    private String rawResponse;
    /** 是 / 不是 / 是或不是 / 不重要 */
    private String answer;
    private String reason;
}
