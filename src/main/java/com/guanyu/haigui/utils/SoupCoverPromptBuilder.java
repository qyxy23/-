package com.guanyu.haigui.utils;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import org.springframework.util.StringUtils;

/**
 * 海龟汤 AI 封面 Prompt 构建（不含汤底，避免剧透与过度写实）
 * 输出比例与前端 {@code soupCover.js} 一致：宽 3 : 高 1，推荐 1200×400
 */
public final class SoupCoverPromptBuilder {

    /** 与前端 SOUP_COVER_OUTPUT_WIDTH 一致，写入 Prompt 约束构图 */
    public static final int COVER_WIDTH = 1200;
    public static final int COVER_HEIGHT = 400;
    public static final String COVER_ASPECT = "3:1";

    private static final int SURFACE_HINT_MAX = 100;

    private SoupCoverPromptBuilder() {
    }

    public static String build(HaiGuiSoup soup) {
        if (soup == null) {
            throw new IllegalArgumentException("故事不存在");
        }
        String title = safe(soup.getSoupTitle(), "推理谜题");
        SoupTag tagEnum = soup.getTags() != null ? soup.getTags() : SoupTag.OTHER;
        String tag = tagEnum.getDescription();
        String tagMood = moodForTag(tagEnum);
        String difficulty = soup.getDifficultyLevel() != null
                ? soup.getDifficultyLevel().getDescription()
                : DifficultyLevel.BEGINNER.getDescription();
        String difficultyHint = hintForDifficulty(soup.getDifficultyLevel());
        String surfaceHint = truncateSurface(soup.getSoupSurface());

        return """
                【任务】为海龟汤推理游戏设计一张列表封面插画，横版横幅，宽高比严格 %s（相当于 %d×%d 像素构图）。
                【构图】主体居中或偏左，右侧留呼吸感空白；画面横向展开，适合手机卡片 3:1 裁剪，四边无文字安全区。
                【题材】标题意境「%s」；氛围标签「%s」——%s；难度「%s」——%s。
                【情境元素】仅取汤面氛围线索，抽象暗示，禁止还原具体剧情答案：%s
                【画风】高质量数字插画，电影级光影或扁平矢量均可；色彩统一、边缘清晰、适合小图缩略展示。
                【禁止】任何中英文文字、字母、数字、水印、Logo、二维码、边框、分格漫画；
                禁止真人照片、写实人脸、可识别明星、商标、版权游戏/动漫角色；
                禁止血腥特写、裸露、政治符号；禁止剧透式关键道具特写。
                """.formatted(
                COVER_ASPECT, COVER_WIDTH, COVER_HEIGHT,
                title, tag, tagMood, difficulty, difficultyHint,
                surfaceHint
        ).trim().replaceAll("\\n\\s*", " ");
    }

    /** 供 API negative_prompt 或日志使用 */
    public static String buildNegativePrompt() {
        return """
                text, words, letters, numbers, watermark, logo, signature, caption, title bar,
                photorealistic face, celebrity, trademark, gore, blood splatter, nudity,
                low quality, blurry, jpeg artifacts, multiple panels, comic strip border
                """.trim().replaceAll("\\s+", " ");
    }

    private static String moodForTag(SoupTag tag) {
        return switch (tag) {
            case HORROR -> "偏暗冷色、悬疑阴影、紧张氛围，避免血腥恐怖特写";
            case HAPPY -> "明快温暖、轻松趣味，无阴暗或惊吓元素";
            case EMOTIONAL -> "柔和色调、人物关系或日常物件的象征性剪影，偏情感向";
            case CREATIVE -> "超现实、脑洞感、非常规构图，但仍需清晰可读";
            case FANTASY -> "奇幻光效、梦境感、非现实场景，避免知名 IP 造型";
            case DAILY -> "生活化场景、自然光、朴素真实，低戏剧冲突";
            case OTHER -> "中性推理悬疑氛围，简洁克制";
        };
    }

    private static String hintForDifficulty(DifficultyLevel level) {
        if (level == null) {
            return "构图简洁、元素较少";
        }
        return switch (level) {
            case BEGINNER -> "构图简洁、主体单一、易于一眼读懂";
            case INTERMEDIATE -> "适度细节与层次，留白与信息平衡";
            case ADVANCED -> "可更复杂的环境与 symbolism，但避免杂乱";
        };
    }

    private static String truncateSurface(String surface) {
        if (!StringUtils.hasText(surface)) {
            return "日常场景中一丝不合常理的悬疑气息";
        }
        String cleaned = surface.replaceAll("[\\r\\n]+", " ").trim();
        if (cleaned.length() <= SURFACE_HINT_MAX) {
            return cleaned;
        }
        return cleaned.substring(0, SURFACE_HINT_MAX) + "…";
    }

    private static String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
