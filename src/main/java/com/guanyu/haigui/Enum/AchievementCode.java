package com.guanyu.haigui.Enum;

import lombok.Getter;

@Getter
public enum AchievementCode {
    FIRST_PLAY("初尝海龟汤", "第一次完整玩完一局海龟汤", AchievementCategory.ONBOARDING, AchievementTier.COMMON, "🐢", 1, 1),
    FIRST_QUESTION("开口问路", "第一次向 AI 提问", AchievementCategory.ONBOARDING, AchievementTier.COMMON, "💬", 1, 1),
    FIRST_SOLO("独行推理", "第一次完成单人 AI 局", AchievementCategory.ONBOARDING, AchievementTier.COMMON, "🧩", 1, 1),
    FIRST_MULTI("凑桌盘汤", "第一次完成多人大厅局", AchievementCategory.ONBOARDING, AchievementTier.COMMON, "👥", 1, 1),

    DISTINCT_SOUP_5("五汤入味", "玩过 5 个不同的海龟汤", AchievementCategory.MILEAGE, AchievementTier.COMMON, "🍲", 5, 5),
    DISTINCT_SOUP_10("十汤老客", "玩过 10 个不同的海龟汤", AchievementCategory.MILEAGE, AchievementTier.RARE, "🏅", 10, 10),
    DISTINCT_SOUP_30("汤品收藏家", "玩过 30 个不同的海龟汤", AchievementCategory.MILEAGE, AchievementTier.RARE, "📚", 30, 30),
    GAME_COMPLETE_10("盘汤十次", "累计完成 10 局", AchievementCategory.MILEAGE, AchievementTier.COMMON, "🔟", 10, 10),
    GAME_COMPLETE_50("资深喝汤人", "累计完成 50 局", AchievementCategory.MILEAGE, AchievementTier.RARE, "🎖️", 50, 50),

    SESSION_ALL_YES("全是「是」", "单局至少 3 次提问，且全部得到「是」", AchievementCategory.SESSION, AchievementTier.RARE, "✅", 1, 1),
    SESSION_ALL_NO("一路「不是」", "单局至少 3 次提问，且全部得到「不是」", AchievementCategory.SESSION, AchievementTier.RARE, "❌", 1, 1),
    PERFECT_PROGRESS("满进度封汤", "本局推理任务进度达到 100%", AchievementCategory.SESSION, AchievementTier.RARE, "💯", 1, 1),
    GUESS_CORRECT("串讲真相", "通过提交推理正确通关", AchievementCategory.SESSION, AchievementTier.RARE, "🎯", 1, 1),
    ALL_CLUES_FOUND("线索全收集", "触发该汤全部线索片段", AchievementCategory.SESSION, AchievementTier.RARE, "🔍", 1, 1),
    QUESTIONS_LEFT_10("惜问如金", "封汤时还剩至少 10 次提问", AchievementCategory.SESSION, AchievementTier.COMMON, "💎", 1, 1),
    EFFICIENT_80("快问快答", "用不超过 15 次提问达到 80% 进度", AchievementCategory.SESSION, AchievementTier.COMMON, "⚡", 1, 1),
    USE_ALL_QUESTIONS("问到最后", "用尽全部提问次数才封汤", AchievementCategory.SESSION, AchievementTier.COMMON, "⏳", 1, 1),
    FIRST_THEORY_SUBMIT("大胆假设", "第一次使用「提交推理」", AchievementCategory.SESSION, AchievementTier.COMMON, "📝", 1, 1),

    FIRST_MVP("本局焦点", "第一次成为大厅 MVP", AchievementCategory.MULTI, AchievementTier.RARE, "⭐", 1, 1),
    MVP_COUNT_5("线索发动机", "累计 5 次成为大厅 MVP", AchievementCategory.MULTI, AchievementTier.RARE, "🚀", 5, 5),
    SESSION_YES_5("方向找对了", "单局个人至少 5 次得到「是」", AchievementCategory.MULTI, AchievementTier.COMMON, "👍", 1, 1),

    FIRST_UPLOAD("提笔造汤", "第一次上传海龟汤", AchievementCategory.UGC, AchievementTier.COMMON, "✍️", 1, 1),
    UPLOAD_APPROVED("汤过审", "第一次审核通过并发布", AchievementCategory.UGC, AchievementTier.RARE, "✨", 1, 1),

    TAG_HORROR_3("惊悚三连", "完成 3 个「惊悚」标签的不同海龟汤", AchievementCategory.TAG, AchievementTier.COMMON, "👻", 3, 3),
    TAG_HAPPY_3("欢乐三连", "完成 3 个「欢乐」标签的不同海龟汤", AchievementCategory.TAG, AchievementTier.COMMON, "😄", 3, 3),
    DIFFICULTY_ADVANCED("困难通关", "首次完成「困难」难度海龟汤", AchievementCategory.TAG, AchievementTier.RARE, "🔥", 1, 1),

    GIVE_UP_FIRST("知难而退", "第一次主动放弃本局", AchievementCategory.HIDDEN, AchievementTier.HIDDEN, "🏳️", 1, 1),
    NIGHT_OWL("夜猫子", "在凌晨完成一局", AchievementCategory.HIDDEN, AchievementTier.HIDDEN, "🌙", 1, 1),
    ZERO_QUESTION_END("静观封汤", "未向 AI 提问即随局结束", AchievementCategory.HIDDEN, AchievementTier.HIDDEN, "🤐", 1, 1);

    private final String title;
    private final String description;
    private final AchievementCategory category;
    private final AchievementTier tier;
    private final String icon;
    private final int defaultTarget;
    private final int sortOrder;

    AchievementCode(String title, String description, AchievementCategory category,
                    AchievementTier tier, String icon, int defaultTarget, int sortOrder) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.tier = tier;
        this.icon = icon;
        this.defaultTarget = defaultTarget;
        this.sortOrder = sortOrder;
    }

    public boolean isProgressBased() {
        return defaultTarget > 1;
    }
}
