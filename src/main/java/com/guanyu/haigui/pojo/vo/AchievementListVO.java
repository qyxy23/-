package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AchievementListVO {
    private int unlockedCount;
    private int totalCount;
    private List<AchievementView> achievements = new ArrayList<>();
}
