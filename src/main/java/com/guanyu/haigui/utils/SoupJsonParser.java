package com.guanyu.haigui.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Enum.ClueType;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.model.ProgressRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * 海龟汤JSON解析工具
 * 负责解析和验证关键线索与进度设置的JSON格式
 */
@Component
@Slf4j
public class SoupJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 分隔符模式，用于识别不同的线索
    private static final Pattern CLUE_SEPARATORS = Pattern.compile("[;；,，、\n\r]+");

    // 线索分类关键词（对应ClueType枚举）
    private static final String[] TIME_KEYWORDS = {
        "时间", "时候", "年代", "何时", "几时", "当天", "之前", "之后", "日期", "当时",
        "半夜", "凌晨", "早上", "上午", "中午", "下午", "晚上", "夜里", "深夜",
        "一点", "两点", "三点", "四点", "五点", "六点", "七点", "八点", "九点", "十点", "十一点", "十二点",
        "一点钟", "两点钟", "三点钟", "四点钟", "五点钟", "六点钟", "七点钟", "八点钟", "九点钟", "十点钟", "十一点钟", "十二点钟",
        "1点", "2点", "3点", "4点", "5点", "6点", "7点", "8点", "9点", "10点", "11点", "12点",
        "0点", "13点", "14点", "15点", "16点", "17点", "18点", "19点", "20点", "21点", "22点", "23点", "24点"
    };
    private static final String[] PLACE_KEYWORDS = {
        "地点", "地方", "位置", "哪里", "何处", "场所", "房间", "屋子", "现场",
        "家里", "公司", "学校", "公园", "街道", "商店", "餐厅", "医院", "警察局",
        "书房", "卧室", "客厅", "厨房", "卫生间", "阳台", "地下室", "车库"
    };
    private static final String[] CHARACTER_KEYWORDS = {
        "人物", "人", "谁", "他", "她", "凶手", "受害者", "主角", "角色", "身份", "姓名",
        "我", "你", "我们", "你们", "他们", "她们", "它们", "自己", "本人",
        "男人", "女人", "男孩", "女孩", "老人", "小孩", "婴儿", "青少年", "成年人",
        "爸爸", "妈妈", "儿子", "女儿", "哥哥", "姐姐", "弟弟", "妹妹", "丈夫", "妻子",
        "朋友", "同事", "同学", "邻居", "陌生人", "客人", "主人", "老板", "员工", "服务员"
    };
    private static final String[] PLOT_KEYWORDS = {
        "为什么", "原因", "经过", "如何", "怎样", "方式", "方法", "情节", "故事", "事件",
        "撒谎", "欺骗", "隐藏", "秘密", "真相", "事实", "证据", "证明", "发现", "知道",
        "做", "干", "搞", "弄", "进行", "实施", "执行", "完成", "开始", "结束", "停止",
        "杀", "死", "伤", "害", "打", "骂", "偷", "抢", "骗", "逃跑", "追", "抓", "找"
    };

    /**
     * 解析关键线索
     * 支持多种输入格式：
     * 1. JSON数组格式：[{"content":"线索1","difficulty":3},{"content":"线索2"}]
     * 2. 分隔符分隔的字符串："线索1；线索2，线索3"
     * 3. 单个字符串："这是一个线索"
     */
    public List<GameClue> parseKeyClues(String input) {
        log.info("开始解析关键线索，输入: '{}'", input);

        if (input == null || input.trim().isEmpty()) {
            log.warn("输入为空或null，返回空列表");
            return new ArrayList<>();
        }

        try {
            // 尝试解析为JSON数组
            JsonNode jsonNode = objectMapper.readTree(input);
            if (jsonNode.isArray()) {
                log.info("检测到JSON数组格式，数量: {}", jsonNode.size());
                return parseCluesFromJson(jsonNode);
            }
        } catch (JsonProcessingException e) {
            log.info("输入不是JSON格式，将作为普通字符串处理: {}", input);
        }

        // 作为普通字符串处理，按分隔符分割
        List<GameClue> clues = parseCluesFromString(input);
        log.info("字符串解析完成，获得线索数量: {}", clues.size());
        return clues;
    }

    /**
     * 解析进度设置
     * 支持多种输入格式：
     * 1. JSON对象格式：{"difficulty":"hard","maxRounds":15}
     * 2. 简化字符串："困难" 或 "20回合30分钟"
     */
    public ProgressRule parseProgressSettings(String input) {
        ProgressRule rule = new ProgressRule();

        if (input == null || input.trim().isEmpty()) {
            return rule;
        }

        try {
            // 尝试解析为JSON对象
            JsonNode jsonNode = objectMapper.readTree(input);
            if (jsonNode.isObject()) {
                return parseRuleFromJson(jsonNode);
            }
        } catch (JsonProcessingException e) {
            log.debug("进度设置不是JSON格式，将作为描述性文本处理: {}", input);
        }

        // 作为描述性文本处理
        return parseRuleFromString(input);
    }

    /**
     * 将线索列表转换为JSON字符串
     */
    public String serializeClues(List<GameClue> clues) {
        try {
            return objectMapper.writeValueAsString(clues);
        } catch (JsonProcessingException e) {
            log.error("序列化线索失败", e);
            return "[]";
        }
    }

    /**
     * 将进度规则转换为JSON字符串
     */
    public String serializeProgressRule(ProgressRule rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            log.error("序列化进度规则失败", e);
            return "{}";
        }
    }

    /**
     * 从JSON数组解析线索
     */
    private List<GameClue> parseCluesFromJson(JsonNode jsonArray) {
        List<GameClue> clues = new ArrayList<>();
        for (JsonNode node : jsonArray) {
            if (node.isObject()) {
                GameClue clue = new GameClue();
                clue.setClueId(node.path("clueId").asText(UUID.randomUUID().toString()));
                clue.setContent(node.path("content").asText());

                // 解析线索类型
                String clueTypeStr = node.path("clueType").asText("");
                if (!clueTypeStr.isEmpty()) {
                    try {
                        clue.setClueType(ClueType.valueOf(clueTypeStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        clue.setClueType(categorizeClueType(node.path("content").asText()));
                    }
                } else {
                    clue.setClueType(categorizeClueType(node.path("content").asText()));
                }

                clue.setDifficulty(node.path("difficulty").asInt(3));
                clue.setIsKey(node.path("isKey").asBoolean(true));
                clue.setHint(node.path("hint").asText());
                clue.setCreatedAtFromLocalDateTime(LocalDateTime.now());
                clues.add(clue);
            } else if (node.isTextual()) {
                GameClue clue = new GameClue(node.asText());
                clue.setClueType(categorizeClueType(node.asText()));
                clue.setDifficulty(determineClueDifficulty(node.asText()));
                clue.setIsKey(determineIfKeyClue(node.asText()));
                clues.add(clue);
            }
        }
        return clues;
    }

    /**
     * 从字符串解析线索（按分隔符分割）
     */
    private List<GameClue> parseCluesFromString(String input) {
        log.info("开始从字符串解析线索: '{}'", input);
        List<GameClue> clues = new ArrayList<>();

        String trimmedInput = input.trim();
        log.info("去除空格后的输入: '{}'", trimmedInput);

        String[] parts = CLUE_SEPARATORS.split(trimmedInput);
        log.info("按分隔符分割后得到 {} 个部分: {}", parts.length, java.util.Arrays.toString(parts));

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            log.info("处理第{}个部分: '{}'", i + 1, part);

            if (!part.isEmpty()) {
                GameClue clue = new GameClue(part);

                // 自动分类线索类型
                ClueType clueType = categorizeClueType(part);
                clue.setClueType(clueType);
                log.info("线索类型: {}", clueType);

                // 自动判断线索难度
                Integer difficulty = determineClueDifficulty(part);
                clue.setDifficulty(difficulty);
                log.info("线索难度: {}", difficulty);

                // 自动判断是否为关键线索
                Boolean isKey = determineIfKeyClue(part);
                clue.setIsKey(isKey);
                log.info("是否关键线索: {}", isKey);

                clues.add(clue);
                log.info("添加线索: {} (类型: {}, 难度: {}, 关键: {})",
                        part, clueType, difficulty, isKey);
            } else {
                log.warn("第{}个部分为空，跳过", i + 1);
            }
        }

        log.info("字符串解析完成，最终线索数量: {}", clues.size());
        return clues;
    }

    /**
     * 从JSON对象解析进度规则
     */
    private ProgressRule parseRuleFromJson(JsonNode jsonNode) {
        ProgressRule rule = new ProgressRule();

        if (jsonNode.has("difficulty")) {
            rule.setDifficulty(jsonNode.path("difficulty").asText());
        }
        if (jsonNode.has("maxRounds")) {
            rule.setMaxRounds(jsonNode.path("maxRounds").asInt());
        }
        if (jsonNode.has("timeLimit")) {
            rule.setTimeLimit(jsonNode.path("timeLimit").asInt());
        }
        if (jsonNode.has("playerCount")) {
            rule.setPlayerCount(jsonNode.path("playerCount").asInt());
        }
        if (jsonNode.has("allowSkip")) {
            rule.setAllowSkip(jsonNode.path("allowSkip").asBoolean());
        }
        if (jsonNode.has("winCondition")) {
            rule.setWinCondition(jsonNode.path("winCondition").asText());
        }
        if (jsonNode.has("requiredClueCount")) {
            rule.setRequiredClueCount(jsonNode.path("requiredClueCount").asInt());
        }

        return rule;
    }

    /**
     * 从描述性文本解析进度规则
     */
    private ProgressRule parseRuleFromString(String input) {
        ProgressRule rule = new ProgressRule();
        String lowerInput = input.toLowerCase();

        // 解析难度
        if (lowerInput.contains("简单") || lowerInput.contains("容易")) {
            rule.setDifficulty("easy");
            rule.setMaxRounds(15);
            rule.setTimeLimit(45);
        } else if (lowerInput.contains("困难") || lowerInput.contains("难")) {
            rule.setDifficulty("hard");
            rule.setMaxRounds(25);
            rule.setTimeLimit(20);
        } else if (lowerInput.contains("中等") || lowerInput.contains("普通")) {
            rule.setDifficulty("medium");
        }

        // 解析回合数
        java.util.regex.Pattern roundsPattern = java.util.regex.Pattern.compile("(\\d+)[回合轮]");
        java.util.regex.Matcher roundsMatcher = roundsPattern.matcher(input);
        if (roundsMatcher.find()) {
            rule.setMaxRounds(Integer.parseInt(roundsMatcher.group(1)));
        }

        // 解析时间限制
        java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("(\\d+)[分钟]");
        java.util.regex.Matcher timeMatcher = timePattern.matcher(input);
        if (timeMatcher.find()) {
            rule.setTimeLimit(Integer.parseInt(timeMatcher.group(1)));
        }

        return rule;
    }

    /**
     * 自动分类线索类型（使用ClueType枚举）
     */
    private ClueType categorizeClueType(String clue) {
        String lowerClue = clue.toLowerCase();

        if (containsAny(lowerClue, TIME_KEYWORDS)) {
            return ClueType.TIME;
        } else if (containsAny(lowerClue, PLACE_KEYWORDS)) {
            return ClueType.PLACE;
        } else if (containsAny(lowerClue, CHARACTER_KEYWORDS)) {
            return ClueType.CHARACTER;
        } else if (containsAny(lowerClue, PLOT_KEYWORDS)) {
            return ClueType.PLOT;
        } else {
            // 默认根据内容特征判断
            if (lowerClue.contains("为什么") || lowerClue.contains("原因")) {
                return ClueType.PLOT;
            } else if (lowerClue.length() > 15 && (lowerClue.contains("的方式") || lowerClue.contains("的过程"))) {
                return ClueType.PLOT;
            } else {
                return ClueType.CHARACTER; // 默认为人物类线索
            }
        }
    }

    /**
     * 自动判断线索难度
     */
    private Integer determineClueDifficulty(String clue) {
        String lowerClue = clue.toLowerCase();

        // 根据关键词判断难度
        if (lowerClue.contains("关键") || lowerClue.contains("重要") || lowerClue.contains("核心")) {
            return 2; // 关键线索相对简单
        } else if (lowerClue.contains("隐藏") || lowerClue.contains("秘密") || lowerClue.contains("微妙")) {
            return 5; // 隐藏线索较难
        } else if (lowerClue.length() > 20) {
            return 4; // 较长线索通常更复杂
        } else {
            return 3; // 默认中等难度
        }
    }

    /**
     * 自动判断是否为关键线索
     */
    private Boolean determineIfKeyClue(String clue) {
        String lowerClue = clue.toLowerCase();

        // 包含关键词汇的通常为关键线索
        if (lowerClue.contains("关键") || lowerClue.contains("重要") || lowerClue.contains("核心") ||
            lowerClue.contains("凶手") || lowerClue.contains("真相") || lowerClue.contains("答案")) {
            return true;
        }

        // 隐藏的、微妙的线索通常也是关键线索
        if (lowerClue.contains("隐藏") || lowerClue.contains("秘密") || lowerClue.contains("暗示")) {
            return true;
        }

        // 时间线索通常是关键线索
        if (containsAny(lowerClue, TIME_KEYWORDS)) {
            return true;
        }

        // 包含"我"的线索通常是关键线索（涉及角色身份和行动）
        if (lowerClue.contains("我") || lowerClue.contains("本人")) {
            return true;
        }

        // 撒谎类线索通常是关键线索
        if (lowerClue.contains("撒谎") || lowerClue.contains("欺骗") || lowerClue.contains("说谎")) {
            return true;
        }

        // 其他情况默认为非关键线索
        return false;
    }

    /**
     * 检查字符串是否包含数组中的任意关键词
     */
    private boolean containsAny(String text, String[] keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }
}