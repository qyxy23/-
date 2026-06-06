package com.guanyu.haigui.utils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 海龟汤 AI 提问前置校验：拦截明显无效输入，避免误扣次数与误触发线索。
 */
public final class SoupQuestionValidator {

    private static final Pattern INVALID_UTTERANCE = Pattern.compile(
            "^(不对|错了|不是|假的|哈+|嗯+|哦+|啊+|额+|呵呵+|哈哈+)[?？!！。…]*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUESTION_MARKER = Pattern.compile(
            "(是不是|是否|有没有|有无|能否|会不会|可不可以|算不算|对吗|么[?？]?|吗[?？]?|[?？])"
                    + "|(什么|谁|哪里|哪儿|为何|为什么|怎么|怎样|多少)"
    );

    private SoupQuestionValidator() {
    }

    /**
     * @return 若无效则返回错误提示，有效则 empty
     */
    public static Optional<String> validate(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return Optional.of("请输入问题");
        }
        String question = rawQuestion.trim();
        if (question.length() < 3) {
            return Optional.of("问题太短，请用完整的封闭疑问句提问");
        }
        if (INVALID_UTTERANCE.matcher(question).matches()) {
            return Optional.of("请用「是不是…」形式的封闭疑问句提问");
        }
        if (question.length() < 6 && !QUESTION_MARKER.matcher(question).find()) {
            return Optional.of("请用「是不是…」形式的封闭疑问句提问");
        }
        return Optional.empty();
    }
}
