package com.guanyu.haigui.pojo.dto;

import lombok.Data;
import com.guanyu.haigui.pojo.model.SoupClue;
import com.guanyu.haigui.pojo.model.ProgressRule;

import java.util.List;

@Data
public class CreateHaiGuiSoupDTO {
    // 标题
    private String soupTitle;

    // 汤
    private String soupSurface;

    // 底
    private String soupBottom;

    // 主持人手册
    private String hostManual;

    // 汤的线索 - 支持多种格式
    // 1. 字符串：可以用分号、逗号、换行分隔多个线索
    // 2. 对象列表：完整的线索对象列表
    private Object keyClues;

    // 汤的进度设置 - 支持多种格式
    // 1. 字符串：简单描述如"困难模式"或"20回合30分钟"
    // 2. 对象：完整的进度设置对象
    private Object progressSettings;

    /**
     * 获取线索字符串（向后兼容）
     */
    public String getKeyCluesAsString() {
        if (keyClues == null) {
            return "";
        }
        if (keyClues instanceof String) {
            return (String) keyClues;
        }
        if (keyClues instanceof List) {
            return String.join("；", ((List<?>) keyClues).stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
        }
        return keyClues.toString();
    }

    /**
     * 获取进度设置字符串（向后兼容）
     */
    public String getProgressSettingsAsString() {
        if (progressSettings == null) {
            return "";
        }
        if (progressSettings instanceof String) {
            return (String) progressSettings;
        }
        return progressSettings.toString();
    }
}
