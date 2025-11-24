package com.guanyu.haigui.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 海龟汤AI增强Prompt工具类
 * 包含8种不同情况的prompt模板
 */
@Component
@Slf4j
public class TurtleSoupPromptUtil {

    /**
     * 情况1：只有汤面和汤底，需要生成进度任务、关键线索、主持人手册
     */
    public static final String PROMPT_TYPE_1 = "TYPE_1_SURFACE_BOTTOM_ONLY";

    /**
     * 情况2：汤面、汤底、进度任务都有，需要生成关键线索、主持人手册
     */
    public static final String PROMPT_TYPE_2 = "TYPE_2_WITH_PROGRESS_TASKS";

    /**
     * 情况3：汤面、汤底、关键线索都有，需要生成进度任务、主持人手册
     */
    public static final String PROMPT_TYPE_3 = "TYPE_3_WITH_KEY_CLUES";

    /**
     * 情况4：汤面、汤底、主持人手册都有，需要生成进度任务、关键线索
     */
    public static final String PROMPT_TYPE_4 = "TYPE_4_WITH_HOST_MANUAL";

    /**
     * 情况5：汤面、汤底、进度任务、关键线索都有，需要生成主持人手册
     */
    public static final String PROMPT_TYPE_5 = "TYPE_5_WITH_PROGRESS_AND_CLUES";

    /**
     * 情况6：汤面、汤底、进度任务、主持人手册都有，需要生成关键线索
     */
    public static final String PROMPT_TYPE_6 = "TYPE_6_WITH_PROGRESS_AND_MANUAL";

    /**
     * 情况7：汤面、汤底、关键线索、主持人手册都有，需要生成进度任务
     */
    public static final String PROMPT_TYPE_7 = "TYPE_7_WITH_CLUES_AND_MANUAL";

    /**
     * 情况8：全部信息都有，进行优化和完善
     */
    public static final String PROMPT_TYPE_8 = "TYPE_8_COMPLETE_OPTIMIZATION";

    /**
     * 根据输入参数确定prompt类型
     */
    public String determinePromptType(String progressSettings, String keyClues, String hostManual) {
        boolean hasProgress = progressSettings != null && !progressSettings.trim().isEmpty();
        boolean hasClues = keyClues != null && !keyClues.trim().isEmpty();
        boolean hasManual = hostManual != null && !hostManual.trim().isEmpty();

        if (!hasProgress && !hasClues && !hasManual) {
            return PROMPT_TYPE_1;
        } else if (hasProgress && !hasClues && !hasManual) {
            return PROMPT_TYPE_2;
        } else if (!hasProgress && hasClues && !hasManual) {
            return PROMPT_TYPE_3;
        } else if (!hasProgress && !hasClues && hasManual) {
            return PROMPT_TYPE_4;
        } else if (hasProgress && hasClues && !hasManual) {
            return PROMPT_TYPE_5;
        } else if (hasProgress && !hasClues && hasManual) {
            return PROMPT_TYPE_6;
        } else if (!hasProgress && hasClues && hasManual) {
            return PROMPT_TYPE_7;
        } else {
            return PROMPT_TYPE_8;
        }
    }

    /**
     * 生成对应类型的prompt
     */
    public String generatePrompt(String promptType, String soupTitle, String soupSurface, String soupBottom,
                                String progressSettings, String keyClues, String hostManual) {

        return switch (promptType) {
            case PROMPT_TYPE_2 -> generatePromptType2(soupSurface, soupBottom, progressSettings);
            case PROMPT_TYPE_3 -> generatePromptType3(soupSurface, soupBottom, keyClues);
            case PROMPT_TYPE_4 -> generatePromptType4(soupSurface, soupBottom, hostManual);
            case PROMPT_TYPE_5 -> generatePromptType5(soupSurface, soupBottom, progressSettings, keyClues);
            case PROMPT_TYPE_6 -> generatePromptType6(soupSurface, soupBottom, progressSettings, hostManual);
            case PROMPT_TYPE_7 -> generatePromptType7(soupSurface, soupBottom, keyClues, hostManual);
            case PROMPT_TYPE_8 ->
                    generatePromptType8(soupTitle, soupSurface, soupBottom, progressSettings, keyClues, hostManual);
            default -> generatePromptType1(soupSurface, soupBottom);
        };
    }

    /**
     * 情况1：只有汤面和汤底，需要生成进度任务、关键线索、主持人手册
     */
    private String generatePromptType1(String soupSurface, String soupBottom) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。请根据以下汤面和汤底，生成完整的游戏内容。

            汤面：%s
            汤底：%s

            请严格按照以下JSON格式返回结果（不要包含任何解释文字，直接返回JSON）：

            {"progressSettings":[{"taskName":"任务名称","description":"任务描述","difficulty":"easy|medium|hard","increment":进度增量}],"keyClues":[{"content":"线索内容","isKey":true,"clueType":"TIME|PLACE|CHARACTER|PLOT"}],"hostManual":"主持人手册内容"}

            重要要求：
            1. 进度设置要循序渐进，难度递增，所有任务的increment总和必须等于100.0
            2. 线索类型必须使用以下枚举值：TIME(时间)、PLACE(地点)、CHARACTER(人物)、PLOT(情节)
            3. 主持人手册要详细说明游戏流程和判断标准
            4. 确保逻辑一致性和可玩性
            5. **严格按照单行JSON格式返回，不要换行，不要解释**
            6. **所有字符串值不要包含换行符，用空格代替**
            7. **increment字段表示进度增量百分比，总和必须为100.0**
            8. **字段名为progressSettings，不是progressSettings**
            """, soupSurface, soupBottom);
    }

    /**
     * 情况2：汤面、汤底、进度任务都有，需要生成关键线索、主持人手册
     */
    private String generatePromptType2(String soupSurface, String soupBottom, String progressSettings) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底和进度任务，请生成对应的关键线索和主持人手册。

            汤面：%s
            汤底：%s
            已有进度任务：%s

            请生成以下内容，并以JSON格式返回：

            {
              "keyClues": [
                {
                  "content": "线索内容",
                  "isKey": true,
                  "clueType": "TIME|PLACE|CHARACTER|PLOT",
                  "difficulty": 1,
                  "hint": "提示信息（可选）"
                }
              ],
              "hostManual": "主持人手册内容，包含游戏流程、线索发放时机、答案判断标准等"
            }

            要求：
            1. 关键线索要与进度任务相匹配
            2. 线索要能引导玩家逐步接近真相
            3. 主持人手册要结合进度任务设计游戏流程
            4. 确保线索的逻辑性和可发现性
            """, soupSurface, soupBottom, progressSettings);
    }

    /**
     * 情况3：汤面、汤底、关键线索都有，需要生成进度任务、主持人手册
     */
    private String generatePromptType3(String soupSurface, String soupBottom, String keyClues) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底和关键线索，请设计合适的进度任务和主持人手册。

            汤面：%s
            汤底：%s
            已有关键线索：%s

            请生成以下内容，并以JSON格式返回：

            {
              "progressSettings": [
                {
                  "taskName": "任务名称",
                  "description": "任务描述",
                  "points": 积分数值,
                  "difficulty": "easy|medium|hard"
                }
              ],
              "hostManual": "主持人手册内容，包含游戏流程、线索发放时机、答案判断标准等"
            }

            要求：
            1. 进度任务要围绕关键线索设计
            2. 任务难度要与线索的重要程度匹配
            3. 主持人手册要说明线索的发放策略
            4. 确保任务与线索的逻辑对应关系
            """, soupSurface, soupBottom, keyClues);
    }

    /**
     * 情况4：汤面、汤底、主持人手册都有，需要生成进度任务、关键线索
     */
    private String generatePromptType4(String soupSurface, String soupBottom, String hostManual) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底和主持人手册，请设计合适的进度任务和关键线索。

            汤面：%s
            汤底：%s
            已有主持人手册：%s

            请生成以下内容，并以JSON格式返回：

            {
              "progressSettings": [
                {
                  "taskName": "任务名称",
                  "description": "任务描述",
                  "points": 积分数值,
                  "difficulty": "easy|medium|hard"
                }
              ],
              "keyClues": [
                {
                  "content": "线索内容",
                  "isKey": true,
                  "clueType": "main|side|red_herring",
                  "difficulty": 1,
                  "hint": "提示信息（可选）"
                }
              ]
            }

            要求：
            1. 进度任务要符合主持人手册的游戏流程设计
            2. 关键线索要能支撑主持人手册中的判断标准
            3. 任务和线索要保持与手册的一致性
            4. 确保游戏的可玩性和平衡性
            """, soupSurface, soupBottom, hostManual);
    }

    /**
     * 情况5：汤面、汤底、进度任务、关键线索都有，需要生成主持人手册
     */
    private String generatePromptType5(String soupSurface, String soupBottom, String progressSettings, String keyClues) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底、进度任务和关键线索，请生成详细的主持人手册。

            汤面：%s
            汤底：%s
            已有进度任务：%s
            已有关键线索：%s

            请生成主持人手册内容，包含：
            1. 游戏整体流程说明
            2. 各个进度任务的具体执行方式
            3. 关键线索的发放时机和方式
            4. 玩家回答的判断标准
            5. 游戏结束条件和获胜规则
            6. 常见问题解答和应对策略

            要求：
            1. 手册要详细具体，便于主持人操作
            2. 要充分考虑玩家可能的提问和回答
            3. 确保游戏的公平性和趣味性
            4. 提供清晰的判断标准和处理流程
            """, soupSurface, soupBottom, progressSettings, keyClues);
    }

    /**
     * 情况6：汤面、汤底、进度任务、主持人手册都有，需要生成关键线索
     */
    private String generatePromptType6(String soupSurface, String soupBottom, String progressSettings, String hostManual) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底、进度任务和主持人手册，请设计合适的关键线索。

            汤面：%s
            汤底：%s
            已有进度任务：%s
            已有主持人手册：%s

            请生成关键线索列表，以JSON格式返回：

            {
              "keyClues": [
                {
                  "content": "线索内容",
                  "isKey": true,
                  "clueType": "main|side|red_herring",
                  "difficulty": 1,
                  "hint": "提示信息（可选）"
                }
              ]
            }

            要求：
            1. 线索要符合进度任务的设计
            2. 要能支撑主持人手册中的判断流程
            3. 线索难度要合理分布
            4. 包含主线线索、支线线索和适当的误导线索
            5. 确保线索能引导玩家逐步接近真相
            """, soupSurface, soupBottom, progressSettings, hostManual);
    }

    /**
     * 情况7：汤面、汤底、关键线索、主持人手册都有，需要生成进度任务
     */
    private String generatePromptType7(String soupSurface, String soupBottom, String keyClues, String hostManual) {
        return String.format("""
            你是一位专业的海龟汤游戏设计师。根据以下已有的汤面、汤底、关键线索和主持人手册，请设计合适的进度任务。

            汤面：%s
            汤底：%s
            已有关键线索：%s
            已有主持人手册：%s

            请生成进度任务列表，以JSON格式返回：

            {
              "progressSettings": [
                {
                  "taskName": "任务名称",
                  "description": "任务描述",
                  "points": 积分数值,
                  "difficulty": "easy|medium|hard"
                }
              ]
            }

            要求：
            1. 任务要与关键线索相匹配
            2. 要符合主持人手册的游戏流程
            3. 任务难度要循序渐进
            4. 积分设置要合理
            5. 确保任务的可完成性和趣味性
            """, soupSurface, soupBottom, keyClues, hostManual);
    }

    /**
     * 情况8：全部信息都有，进行优化和完善
     */
    private String generatePromptType8(String soupTitle, String soupSurface, String soupBottom,
                                     String progressSettings, String keyClues, String hostManual) {
        return String.format("""
            你是一位专业的海龟汤游戏优化师。请审查并优化以下完整的海龟汤游戏内容，提出改进建议。

            标题：%s
            汤面：%s
            汤底：%s
            进度任务：%s
            关键线索：%s
            主持人手册：%s

            请分析并优化以下方面：

            1. **标题优化**：如果标题不够吸引人或不够准确，请提供改进建议

            2. **进度任务优化**：
               - 检查任务难度递进是否合理
               - 积分设置是否平衡
               - 任务描述是否清晰

            3. **关键线索优化**：
               - 线索是否足够引导玩家
               - 线索难度分布是否合理
               - 是否需要增加或减少某些线索

            4. **主持人手册优化**：
               - 流程说明是否清晰
               - 判断标准是否完善
               - 是否遗漏重要环节

            请以JSON格式返回优化建议：

            {
              "titleOptimization": {
                "original": "原标题",
                "suggested": "建议标题",
                "reason": "优化原因"
              },
              "progressSettingsOptimization": {
                "issues": ["发现的问题"],
                "suggestions": ["改进建议"],
                "optimizedTasks": [优化后的任务列表]
              },
              "keyCluesOptimization": {
                "issues": ["发现的问题"],
                "suggestions": ["改进建议"],
                "optimizedClues": [优化后的线索列表]
              },
              "hostManualOptimization": {
                "issues": ["发现的问题"],
                "suggestions": ["改进建议"],
                "optimizedManual": "优化后的主持人手册"
              }
            }

            要求：
            1. 保持原有内容的完整性
            2. 重点优化逻辑一致性和可玩性
            3. 确保优化后的内容更加完善和平衡
            4. 提供具体的优化理由
            """, soupTitle, soupSurface, soupBottom, progressSettings, keyClues, hostManual);
    }
}