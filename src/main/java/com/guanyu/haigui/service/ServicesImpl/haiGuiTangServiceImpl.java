package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.manager.AIManager;
import com.guanyu.haigui.pojo.dto.TitleGenerateDTO;
import com.guanyu.haigui.pojo.dto.TurtleSoupEnhanceDTO;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.pojo.vo.TitleGenerateResultVO;
import com.guanyu.haigui.pojo.vo.TurtleSoupEnhanceResultVO;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.TurtleSoupPromptUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class haiGuiTangServiceImpl {

    private final AIManager aiManager;
    private final ObjectMapper objectMapper;
    private final TurtleSoupPromptUtil promptUtil;

    // 调试模式配置
    @Value("${haiqutang.ai.debug-mode:false}")
    private boolean debugMode;

    public haiGuiTangServiceImpl(AIManager aiManager, ObjectMapper objectMapper, TurtleSoupPromptUtil promptUtil) {
        this.aiManager = aiManager;
        this.objectMapper = objectMapper;
        this.promptUtil = promptUtil;
    }

    /**
     * 海龟汤AI增强功能
     * 根据用户输入的内容，调用AI生成完善的海龟汤信息
     */
    public TurtleSoupEnhanceResultVO enhanceTurtleSoup(TurtleSoupEnhanceDTO enhanceDTO) {
        try {
            log.info("开始海龟汤AI增强，标题: {}", enhanceDTO.getSoupTitle());

            // 1. 确定prompt类型
            String promptType = promptUtil.determinePromptType(
                    enhanceDTO.getProgressTasks(),
                    enhanceDTO.getKeyClues(),
                    enhanceDTO.getHostManual()
            );

            log.info("使用prompt类型: {}", promptType);

            // 2. 生成prompt
            String prompt = promptUtil.generatePrompt(
                    promptType,
                    enhanceDTO.getSoupTitle(),
                    enhanceDTO.getSoupSurface(),
                    enhanceDTO.getSoupBottom(),
                    enhanceDTO.getProgressTasks(),
                    enhanceDTO.getKeyClues(),
                    enhanceDTO.getHostManual()
            );

            log.info("生成prompt长度: {}", prompt.length());

            // 3. 调用AI（或使用模拟数据）
            String aiResponse;
            if (debugMode) {
                aiResponse = getMockAiResponse();
                log.info("使用模拟AI响应，调试模式已启用");
            } else {
                String systemPrompt = "你是一位专业的海龟汤游戏设计师，擅长设计逻辑严密、趣味性强的海龟汤游戏。请严格按照JSON格式返回结果。";
                aiResponse = aiManager.doChat(systemPrompt, prompt);
                log.info("调用真实AI服务");
            }

            log.info("AI响应长度: {}", aiResponse.length());

            // 4. 解析AI响应
            return parseAIResponse(aiResponse, promptType, enhanceDTO);

        } catch (Exception e) {
            log.error("海龟汤AI增强失败", e);
            TurtleSoupEnhanceResultVO errorResult = new TurtleSoupEnhanceResultVO();
            errorResult.setStatus("AI增强失败: " + e.getMessage());
            errorResult.setPromptType("ERROR");
            return errorResult;
        }
    }

    /**
     * 解析AI响应
     */
    private TurtleSoupEnhanceResultVO parseAIResponse(String aiResponse, String promptType, TurtleSoupEnhanceDTO enhanceDTO) {
        TurtleSoupEnhanceResultVO result = new TurtleSoupEnhanceResultVO();
        result.setPromptType(promptType);
        result.setStatus("success");

        try {
            // 如果是调试模式，直接从原始响应中提取JSON字段
            if (debugMode) {
                return parseMockResponse(aiResponse, promptType, enhanceDTO);
            }

            // 非调试模式，正常解析AI响应
            String cleanedResponse = cleanJsonResponse(aiResponse);
            log.info("清理后的AI响应长度: {}", cleanedResponse.length());

            // 尝试解析JSON响应
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(cleanedResponse, Map.class);

            // 根据不同prompt类型提取数据
            switch (promptType) {
                case TurtleSoupPromptUtil.PROMPT_TYPE_1:
                    // 生成所有内容
                    extractAllFields(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_2:
                    // 只生成关键线索和主持人手册
                    result.setProgressSettings(enhanceDTO.getProgressTasks()); // 保留原有进度设置
                    extractKeyCluesAndManual(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_3:
                    // 只生成进度设置和主持人手册
                    result.setKeyClues(enhanceDTO.getKeyClues()); // 保留原有关键线索
                    extractProgressSettingsAndManual(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_4:
                    // 只生成进度设置和关键线索
                    result.setHostManual(enhanceDTO.getHostManual()); // 保留原有主持人手册
                    extractProgressSettingsAndClues(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_5:
                    // 只生成主持人手册
                    result.setProgressSettings(enhanceDTO.getProgressTasks()); // 保留原有进度设置
                    result.setKeyClues(enhanceDTO.getKeyClues()); // 保留原有关键线索
                    result.setHostManual(extractStringField(responseMap, "hostManual"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_6:
                    // 只生成关键线索
                    result.setProgressSettings(enhanceDTO.getProgressTasks()); // 保留原有进度设置
                    result.setHostManual(enhanceDTO.getHostManual()); // 保留原有主持人手册
                    result.setKeyClues(extractJsonField(responseMap, "keyClues"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_7:
                    // 只生成进度设置
                    result.setKeyClues(enhanceDTO.getKeyClues()); // 保留原有关键线索
                    result.setHostManual(enhanceDTO.getHostManual()); // 保留原有主持人手册
                    result.setProgressSettings(extractJsonField(responseMap, "progressSettings"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_8:
                    // 优化建议，返回优化后的完整内容
                    extractOptimizedContent(responseMap, result, enhanceDTO);
                    break;
                default:
                    result.setStatus("未知的prompt类型: " + promptType);
                    break;
            }

        } catch (Exception e) {
            log.error("解析AI响应失败", e);
            result.setStatus("解析AI响应失败: " + e.getMessage());
            // 如果解析失败，返回原始AI响应
            result.setHostManual("AI响应解析失败，原始响应: " + aiResponse);
        }

        return result;
    }

    /**
     * 提取所有字段
     */
    private void extractAllFields(Map<String, Object> responseMap, TurtleSoupEnhanceResultVO result) {
        // 调试模式下直接使用原始值，避免双重JSON序列化
        if (debugMode) {
            result.setProgressSettings(responseMap.get("progressSettings").toString());
            result.setKeyClues(responseMap.get("keyClues").toString());
            result.setHostManual(responseMap.get("hostManual").toString());
        } else {
            result.setProgressSettings(extractJsonField(responseMap, "progressSettings"));
            result.setKeyClues(extractJsonField(responseMap, "keyClues"));
            result.setHostManual(extractStringField(responseMap, "hostManual"));
        }
    }

    /**
     * 提取关键线索和主持人手册
     */
    private void extractKeyCluesAndManual(Map<String, Object> responseMap, TurtleSoupEnhanceResultVO result) {
        if (debugMode) {
            result.setKeyClues(responseMap.get("keyClues").toString());
            result.setHostManual(responseMap.get("hostManual").toString());
        } else {
            result.setKeyClues(extractJsonField(responseMap, "keyClues"));
            result.setHostManual(extractStringField(responseMap, "hostManual"));
        }
    }

    /**
     * 提取进度设置和主持人手册
     */
    private void extractProgressSettingsAndManual(Map<String, Object> responseMap, TurtleSoupEnhanceResultVO result) {
        if (debugMode) {
            result.setProgressSettings(responseMap.get("progressSettings").toString());
            result.setHostManual(responseMap.get("hostManual").toString());
        } else {
            result.setProgressSettings(extractJsonField(responseMap, "progressSettings"));
            result.setHostManual(extractStringField(responseMap, "hostManual"));
        }
    }

    /**
     * 提取进度设置和关键线索
     */
    private void extractProgressSettingsAndClues(Map<String, Object> responseMap, TurtleSoupEnhanceResultVO result) {
        if (debugMode) {
            result.setProgressSettings(responseMap.get("progressSettings").toString());
            result.setKeyClues(responseMap.get("keyClues").toString());
        } else {
            result.setProgressSettings(extractJsonField(responseMap, "progressSettings"));
            result.setKeyClues(extractJsonField(responseMap, "keyClues"));
        }
    }

    /**
     * 提取优化后的内容
     */
    private void extractOptimizedContent(Map<String, Object> responseMap, TurtleSoupEnhanceResultVO result, TurtleSoupEnhanceDTO original) {
        // 提取进度设置优化内容
        @SuppressWarnings("unchecked")
        Map<String, Object> tasksOpt = (Map<String, Object>) responseMap.get("progressSettingsOptimization");
        if (tasksOpt != null && tasksOpt.get("optimizedSettings") != null) {
            // 直接使用优化后的字符串，避免二次序列化
            result.setProgressSettings(tasksOpt.get("optimizedSettings").toString());
        } else {
            result.setProgressSettings(original.getProgressTasks());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> cluesOpt = (Map<String, Object>) responseMap.get("keyCluesOptimization");
        if (cluesOpt != null && cluesOpt.get("optimizedClues") != null) {
            // 直接使用优化后的字符串，避免二次序列化
            result.setKeyClues(cluesOpt.get("optimizedClues").toString());
        } else {
            result.setKeyClues(original.getKeyClues());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> manualOpt = (Map<String, Object>) responseMap.get("hostManualOptimization");
        if (manualOpt != null) {
            result.setHostManual(extractStringField(manualOpt, "optimizedManual"));
        } else {
            result.setHostManual(original.getHostManual());
        }
    }

    /**
     * 提取字符串字段
     */
    private String extractStringField(Map<String, Object> map, String fieldName) {
        Object value = map.get(fieldName);
        return value != null ? value.toString() : null;
    }

    /**
     * 提取JSON字段
     */
    private String extractJsonField(Map<String, Object> map, String fieldName) {
        Object value = map.get(fieldName);
        if (value == null) {
            return null;
        }

        // 如果是调试模式，直接返回原始字符串（避免二次序列化）
        if (debugMode) {
            return value.toString();
        }

        // 非调试模式，正常序列化
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON字段序列化失败: {}", fieldName, e);
            return value.toString();
        }
    }


    public SingleEncodeResponse vectorSignalTurtleSoup(String content) {
        // 初始化客户端（替换为你的服务地址）
        return BgeVectorClientUtil.encodeSingle(content);
    }


    public BatchEncodeResponse vectorTurtleSoup(List<String> content) {
        return BgeVectorClientUtil.encodeBatch(content);
    }

    public String generateHostManual(String content) {
        // 保持向后兼容
        return "";
    }

    public String generateKeyClue(String content) {
        // 保持向后兼容
        return "";
    }

    public String generateProgressSetting(String content) {
        // 保持向后兼容
        return "";
    }

    /**
     * 清理AI响应中的JSON格式问题
     * 处理未转义的换行符、控制字符等
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }

        try {
            // 1. 查找JSON的开始和结束
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}");

            if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
                log.warn("无法找到有效的JSON结构，返回空对象");
                return "{}";
            }

            String jsonPart = response.substring(startIndex, endIndex + 1);

            // 2. 清理字符串值中的换行符和其他控制字符
            jsonPart = jsonPart.replaceAll("\"([^\"]*?)\\s*\\n\\s*([^\"]*?)\"", "\"$1 $2\"");
            jsonPart = jsonPart.replaceAll("\\r\\n", " ");
            jsonPart = jsonPart.replaceAll("\\n", " ");
            jsonPart = jsonPart.replaceAll("\\r", " ");
            jsonPart = jsonPart.replaceAll("\\t", " ");

            // 3. 移除多余空格
            jsonPart = jsonPart.replaceAll("\\s+", " ");
            jsonPart = jsonPart.trim();

            // 4. 尝试验证JSON格式
            objectMapper.readTree(jsonPart);

            log.info("JSON清理成功，原始长度: {}, 清理后长度: {}", response.length(), jsonPart.length());
            return jsonPart;

        } catch (Exception e) {
            log.error("JSON清理失败，尝试备用方法", e);

            // 备用方法：尝试提取JSON对象
            try {
                return extractJsonFromResponse(response);
            } catch (Exception e2) {
                log.error("备用JSON提取方法也失败", e2);
                log.warn("AI响应原始内容: {}", response);
                return "{\"error\": \"JSON解析失败\", \"originalResponse\": \"" +
                       response.replaceAll("\"", "\\\\\"") + "\"}";
            }
        }
    }

    /**
     * 备用JSON提取方法
     * 尝试从复杂响应中提取JSON部分
     */
    private String extractJsonFromResponse(String response) {
        // 查找可能的JSON起始位置
        String[] possibleStarts = {"{", "\\{", "```json", "{\n"};
        String[] possibleEnds = {"}", "\\}", "```", "}\n"};

        int startIndex = -1;
        int endIndex = -1;

        for (String start : possibleStarts) {
            int index = response.indexOf(start);
            if (index != -1) {
                startIndex = index + start.replace("\\", "").replace("```json", "").length();
                break;
            }
        }

        if (startIndex == -1) {
            startIndex = response.indexOf("{");
        }

        for (String end : possibleEnds) {
            int index = response.lastIndexOf(end);
            if (index != -1 && index > startIndex) {
                endIndex = index;
                break;
            }
        }

        if (endIndex == -1) {
            endIndex = response.lastIndexOf("}");
        }

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            String extracted = response.substring(startIndex, endIndex + 1);
            // 简单清理
            extracted = extracted.replaceAll("[^\\x20-\\x7E]", " ");
            extracted = extracted.replaceAll("\\s+", " ");
            return extracted;
        }

        throw new RuntimeException("无法提取有效的JSON");
    }

    /**
     * 解析调试模式下的模拟响应
     * 直接从JSON字符串中提取字段，避免二次序列化
     */
    private TurtleSoupEnhanceResultVO parseMockResponse(String aiResponse, String promptType, TurtleSoupEnhanceDTO enhanceDTO) {
        TurtleSoupEnhanceResultVO result = new TurtleSoupEnhanceResultVO();
        result.setPromptType(promptType);
        result.setStatus("success");

        try {
            log.info("调试模式：直接解析模拟响应，长度: {}", aiResponse.length());

            // 直接解析JSON并提取字段
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(aiResponse, Map.class);

            // 根据不同prompt类型提取数据
            switch (promptType) {
                case TurtleSoupPromptUtil.PROMPT_TYPE_1:
                    extractAllFields(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_2:
                    result.setProgressSettings(enhanceDTO.getProgressTasks());
                    extractKeyCluesAndManual(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_3:
                    result.setKeyClues(enhanceDTO.getKeyClues());
                    extractProgressSettingsAndManual(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_4:
                    result.setHostManual(enhanceDTO.getHostManual());
                    extractProgressSettingsAndClues(responseMap, result);
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_5:
                    result.setProgressSettings(enhanceDTO.getProgressTasks());
                    result.setKeyClues(enhanceDTO.getKeyClues());
                    result.setHostManual(extractStringField(responseMap, "hostManual"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_6:
                    result.setProgressSettings(enhanceDTO.getProgressTasks());
                    result.setHostManual(enhanceDTO.getHostManual());
                    result.setKeyClues(extractJsonField(responseMap, "keyClues"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_7:
                    result.setKeyClues(enhanceDTO.getKeyClues());
                    result.setHostManual(enhanceDTO.getHostManual());
                    result.setProgressSettings(extractJsonField(responseMap, "progressSettings"));
                    break;
                case TurtleSoupPromptUtil.PROMPT_TYPE_8:
                    extractOptimizedContent(responseMap, result, enhanceDTO);
                    break;
                default:
                    result.setStatus("未知的prompt类型: " + promptType);
                    break;
            }

            log.info("调试模式解析成功 - 进度任务: {}, 关键线索: {}, 主持人手册: {}",
                    result.getProgressSettings() != null ? "已生成" : "未生成",
                    result.getKeyClues() != null ? "已生成" : "未生成",
                    result.getHostManual() != null ? "已生成" : "未生成");

        } catch (Exception e) {
            log.error("调试模式解析失败", e);
            result.setStatus("调试模式解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取模拟AI响应数据（调试模式使用）
     * @return 模拟的AI响应JSON字符串
     */
    private String getMockAiResponse() {
        try {
            ClassPathResource resource = new ClassPathResource("temp/aiResponse.txt");
            if (!resource.exists()) {
                log.error("模拟AI响应文件不存在: temp/aiResponse.txt");
                return getFallbackMockResponse();
            }

            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            String mockResponse = new String(data, StandardCharsets.UTF_8);

            log.info("成功读取模拟AI响应，文件大小: {} 字节", mockResponse.length());
            return mockResponse;

        } catch (IOException e) {
            log.error("读取模拟AI响应文件失败", e);
            return getFallbackMockResponse();
        }
    }

    /**
     * 根据汤面汤底生成标题
     * @param titleGenerateDTO 标题生成请求
     * @return 标题生成结果
     */
    public TitleGenerateResultVO generateTitle(TitleGenerateDTO titleGenerateDTO) {
        try {
            log.info("开始生成海龟汤标题");

            TitleGenerateResultVO result = new TitleGenerateResultVO();

            if (debugMode) {
                // 调试模式：返回模拟标题
                result.setGeneratedTitle("墓碑上的文字");
                result.setStatus("success");
                result.setTitleType("optimized");
                result.setSuggestion("调试模式下返回模拟标题");
            } else {
                // 生产模式：调用AI生成标题
                String prompt = generateTitlePrompt(titleGenerateDTO);
                String systemPrompt = "你是一位专业的海龟汤标题设计师，擅长创作引人入胜、富有悬念的标题。";

                String aiResponse = aiManager.doChat(systemPrompt, prompt);

                // 解析AI响应
                result.setGeneratedTitle(extractTitleFromResponse(aiResponse));
                result.setStatus("success");
                result.setTitleType("optimized");
                result.setSuggestion("AI生成的标题更适合吸引玩家");
            }

            log.info("标题生成成功: {}", result.getGeneratedTitle());
            return result;

        } catch (Exception e) {
            log.error("标题生成失败", e);
            TitleGenerateResultVO errorResult = new TitleGenerateResultVO();
            errorResult.setStatus("生成失败: " + e.getMessage());
            errorResult.setGeneratedTitle(titleGenerateDTO.getCurrentTitle() != null ?
                                          titleGenerateDTO.getCurrentTitle() : "未命名海龟汤");
            return errorResult;
        }
    }

    /**
     * 生成标题提示词
     */
    private String generateTitlePrompt(TitleGenerateDTO dto) {
        return String.format("""
            请根据以下海龟汤的汤面和汤底，生成一个引人入胜的标题。

            汤面：%s
            汤底：%s

            要求：
            1. 标题要有悬念感，能吸引玩家
            2. 长度控制在2-8个汉字之间
            3. 避免直接剧透汤底内容
            4. 体现故事的核心矛盾
            5. 具有海龟汤游戏的特色

            请直接返回生成的标题，不要包含任何解释。
            """, dto.getSoupSurface(), dto.getSoupBottom());
    }

    /**
     * 从AI响应中提取标题
     */
    private String extractTitleFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "未命名海龟汤";
        }

        // 清理响应，去除可能的引号和额外说明
        String title = aiResponse.trim()
                               .replaceAll("^['\"]+", "")
                               .replaceAll("['\"]+$", "")
                               .split("\n")[0]
                               .trim();

        if (title.length() > 10) {
            title = title.substring(0, 10) + "...";
        }

        return title.isEmpty() ? "未命名海龟汤" : title;
    }

    /**
     * 备用模拟响应（当文件读取失败时使用）
     * @return 默认的模拟响应
     */
    private String getFallbackMockResponse() {
        return "{\"progressSettings\":[{\"taskName\":\"调查任务\",\"description\":\"这是一个调查任务\",\"difficulty\":\"easy\",\"increment\":100.0}],\"keyClues\":[{\"content\":\"这是一个线索\",\"isKey\":true,\"clueType\":\"PLOT\"}],\"hostManual\":\"这是主持人手册内容\"}";
    }

}
