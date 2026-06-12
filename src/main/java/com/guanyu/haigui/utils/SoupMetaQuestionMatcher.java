package com.guanyu.haigui.utils;

import com.guanyu.haigui.Enum.ContentTone;
import com.guanyu.haigui.Enum.LogicMode;
import com.guanyu.haigui.Enum.QuestionWithAiAnswer;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 纯元问题快路径：根据 {@code logic_mode} / {@code content_tone} 直接作答，不调 LLM。
 * <p>
 * 仅匹配明确的「是本格吗」「是清汤还是红汤」等问法；字段为 null 时不处理，交由 AI 判题。
 */
public final class SoupMetaQuestionMatcher {

    private static final Pattern IS_ORTHODOX = Pattern.compile(
            ".*(是本格|是不是本格|是否本格|算不算本格).*[吗?？].*");
    private static final Pattern IS_VARIANT = Pattern.compile(
            ".*(是变格|是不是变格|是否变格|算不算变格).*[吗?？].*");
    private static final Pattern ORTHODOX_OR_VARIANT = Pattern.compile(
            ".*(是本格还是变格|是变格还是本格).*");

    private static final Pattern IS_CLEAR = Pattern.compile(
            ".*(是清汤|是不是清汤|是否清汤|算不算清汤).*[吗?？].*");
    private static final Pattern IS_RED = Pattern.compile(
            ".*(是红汤|是不是红汤|是否红汤|算不算红汤).*[吗?？].*");
    private static final Pattern IS_BLACK = Pattern.compile(
            ".*(是黑汤|是不是黑汤|是否黑汤|算不算黑汤).*[吗?？].*");
    private static final Pattern CLEAR_OR_RED = Pattern.compile(
            ".*(是清汤还是红汤|是红汤还是清汤).*");

    private SoupMetaQuestionMatcher() {
    }

    /**
     * @return 命中元问题且字段足够时返回答案枚举，否则 empty 走 AI
     */
    public static Optional<QuestionWithAiAnswer> tryResolve(
            String rawQuestion, LogicMode logicMode, ContentTone contentTone) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return Optional.empty();
        }
        String question = rawQuestion.trim();

        if (ORTHODOX_OR_VARIANT.matcher(question).matches()) {
            return resolveOrthodoxOrVariant(logicMode);
        }
        if (IS_ORTHODOX.matcher(question).matches()) {
            return resolveIsOrthodox(logicMode);
        }
        if (IS_VARIANT.matcher(question).matches()) {
            return resolveIsVariant(logicMode);
        }
        if (CLEAR_OR_RED.matcher(question).matches()) {
            return resolveClearOrRed(contentTone);
        }
        if (IS_CLEAR.matcher(question).matches()) {
            return resolveIsClear(contentTone);
        }
        if (IS_RED.matcher(question).matches()) {
            return resolveIsRed(contentTone);
        }
        if (IS_BLACK.matcher(question).matches()) {
            return resolveIsBlack(contentTone);
        }
        return Optional.empty();
    }

    private static Optional<QuestionWithAiAnswer> resolveIsOrthodox(LogicMode logicMode) {
        if (logicMode == null) {
            return Optional.empty();
        }
        return Optional.of(logicMode == LogicMode.ORTHODOX
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    private static Optional<QuestionWithAiAnswer> resolveIsVariant(LogicMode logicMode) {
        if (logicMode == null) {
            return Optional.empty();
        }
        return Optional.of(logicMode == LogicMode.VARIANT
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    /** 「是本格还是变格」→ 是否更偏变格 */
    private static Optional<QuestionWithAiAnswer> resolveOrthodoxOrVariant(LogicMode logicMode) {
        if (logicMode == null) {
            return Optional.empty();
        }
        return Optional.of(logicMode == LogicMode.VARIANT
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    private static Optional<QuestionWithAiAnswer> resolveIsClear(ContentTone contentTone) {
        if (contentTone == null) {
            return Optional.empty();
        }
        return Optional.of(contentTone == ContentTone.CLEAR
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    private static Optional<QuestionWithAiAnswer> resolveIsRed(ContentTone contentTone) {
        if (contentTone == null) {
            return Optional.empty();
        }
        return Optional.of(contentTone == ContentTone.RED
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    private static Optional<QuestionWithAiAnswer> resolveIsBlack(ContentTone contentTone) {
        if (contentTone == null) {
            return Optional.empty();
        }
        return Optional.of(contentTone == ContentTone.BLACK
                ? QuestionWithAiAnswer.YES : QuestionWithAiAnswer.NO);
    }

    /** 「是清汤还是红汤」→ 是否更偏红汤（红汤、黑汤均答「是」） */
    private static Optional<QuestionWithAiAnswer> resolveClearOrRed(ContentTone contentTone) {
        if (contentTone == null) {
            return Optional.empty();
        }
        return switch (contentTone) {
            case CLEAR -> Optional.of(QuestionWithAiAnswer.NO);
            case RED, BLACK -> Optional.of(QuestionWithAiAnswer.YES);
        };
    }
}
