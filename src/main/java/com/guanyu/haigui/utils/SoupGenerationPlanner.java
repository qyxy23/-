package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import lombok.Getter;

/**
 * 审核生成前的本地规划：线索/任务数量区间与密度提示（不调用小模型）。
 */
public final class SoupGenerationPlanner {

    private SoupGenerationPlanner() {
    }

    public static Plan plan(HaiGuiSoupAudit audit) {
        String surface = nullToEmpty(audit.getSurface());
        String bottom = nullToEmpty(audit.getBottom());
        int surfaceLen = surface.length();
        int bottomLen = bottom.length();
        int difficulty = audit.getDifficultyLevel() != null
                ? audit.getDifficultyLevel().ordinal()
                : 0;
        int duration = audit.getEstimatedDuration() != null ? audit.getEstimatedDuration() : 30;

        // 汤面权重低、汤底权重高；短汤面+长汤底常见于高隐含信息
        double infoScore = surfaceLen * 0.3 + bottomLen * 1.0;
        double densityRatio = bottomLen / (double) Math.max(surfaceLen, 40);
        int densityBonus = densityBonus(densityRatio);

        int fragmentMin = 4 + difficulty + (duration >= 45 ? 1 : 0);
        fragmentMin = clamp(fragmentMin, 4, 10);

        int fragmentMax = (int) Math.round(6 + infoScore / 80.0 + difficulty * 2.0 + densityBonus);
        if (duration >= 45) {
            fragmentMax += 1;
        }
        fragmentMax = clamp(fragmentMax, fragmentMin, 20);

        int fragmentTarget = (fragmentMin + fragmentMax) / 2;
        if (densityBonus >= 2) {
            fragmentTarget = (int) Math.round(fragmentMin * 0.25 + fragmentMax * 0.75);
        }
        fragmentTarget = clamp(fragmentTarget, fragmentMin, fragmentMax);

        int taskTarget = 4;
        if (difficulty == 0 && bottomLen < 400) {
            taskTarget = 3;
        } else if (difficulty == 2 || bottomLen > 1200 || densityBonus >= 4) {
            taskTarget = 5;
        }

        String densityHint = buildDensityHint(surfaceLen, bottomLen, densityRatio, densityBonus);
        return new Plan(fragmentMin, fragmentMax, fragmentTarget, 3, 5, taskTarget, densityHint);
    }

    private static int densityBonus(double densityRatio) {
        if (densityRatio >= 3.0) {
            return 4;
        }
        if (densityRatio >= 2.0) {
            return 2;
        }
        if (densityRatio >= 1.5) {
            return 1;
        }
        return 0;
    }

    private static String buildDensityHint(int surfaceLen, int bottomLen, double densityRatio, int densityBonus) {
        if (densityBonus >= 4) {
            return String.format(
                    "汤面较短（%d字）但汤底较长（%d字），密度比≈%.1f，隐含转折多，请按汤底事实层/转折层拆分，可接近上限。",
                    surfaceLen, bottomLen, densityRatio);
        }
        if (densityBonus >= 2) {
            return String.format(
                    "汤面（%d字）相对精简、汤底（%d字）信息更厚，密度比≈%.1f，请优先按推理层次拆分，不要为凑数重复。",
                    surfaceLen, bottomLen, densityRatio);
        }
        if (bottomLen < 300) {
            return String.format("汤底较短（%d字），线索宜精炼，可接近下限，禁止编造。", bottomLen);
        }
        return String.format("汤面%d字、汤底%d字，按汤底可验证事实点拆分即可。", surfaceLen, bottomLen);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @Getter
    public static final class Plan {
        private final int fragmentMin;
        private final int fragmentMax;
        private final int fragmentTarget;
        private final int taskMin;
        private final int taskMax;
        private final int taskTarget;
        private final String densityHint;

        public Plan(int fragmentMin, int fragmentMax, int fragmentTarget,
                    int taskMin, int taskMax, int taskTarget, String densityHint) {
            this.fragmentMin = fragmentMin;
            this.fragmentMax = fragmentMax;
            this.fragmentTarget = fragmentTarget;
            this.taskMin = taskMin;
            this.taskMax = taskMax;
            this.taskTarget = taskTarget;
            this.densityHint = densityHint;
        }
    }
}
