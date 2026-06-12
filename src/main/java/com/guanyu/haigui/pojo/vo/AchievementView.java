package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.AchievementCategory;
import com.guanyu.haigui.Enum.AchievementCode;
import com.guanyu.haigui.Enum.AchievementTier;
import com.guanyu.haigui.pojo.model.UserAchievement;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AchievementView {

    private String code;
    private String name;
    private String description;
    private String category;
    private String categoryLabel;
    private String tier;
    private String icon;
    private boolean unlocked;
    private LocalDateTime unlockedAt;
    private int progress;
    private int target;
    private int sortOrder;

    public static AchievementView fromDefinition(AchievementCode definition, UserAchievement record, boolean maskHidden) {
        AchievementView view = new AchievementView();
        view.setCode(definition.name());
        view.setCategory(definition.getCategory().name());
        view.setCategoryLabel(definition.getCategory().getLabel());
        view.setTier(definition.getTier().name());
        view.setIcon(definition.getIcon());
        view.setTarget(definition.getDefaultTarget());
        view.setSortOrder(definition.getSortOrder());

        boolean hiddenLocked = maskHidden
                && definition.getTier() == AchievementTier.HIDDEN
                && (record == null || record.getUnlockedAt() == null);

        if (hiddenLocked) {
            view.setName("???");
            view.setDescription("隐藏成就，解锁后可见");
            view.setUnlocked(false);
            view.setProgress(0);
            return view;
        }

        view.setName(definition.getTitle());
        view.setDescription(definition.getDescription());

        if (record != null) {
            view.setUnlocked(record.getUnlockedAt() != null);
            view.setUnlockedAt(record.getUnlockedAt());
            view.setProgress(record.getProgress() != null ? record.getProgress() : 0);
            view.setTarget(record.getTarget() != null ? record.getTarget() : definition.getDefaultTarget());
        } else {
            view.setUnlocked(false);
            view.setProgress(0);
        }
        return view;
    }

    public static AchievementView fromUnlocked(AchievementCode definition, UserAchievement record) {
        AchievementView view = fromDefinition(definition, record, false);
        view.setUnlocked(true);
        view.setUnlockedAt(record.getUnlockedAt());
        return view;
    }
}
