package com.guanyu.haigui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * JSON解析修复验证测试
 */
@SpringBootTest
public class JsonParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试模拟响应的JSON解析
     */
    @Test
    public void testMockResponseParsing() throws Exception {
        String mockResponse = "{\"progressTasks\":[{\"taskName\":\"测试任务\",\"description\":\"测试描述\",\"points\":10,\"difficulty\":\"easy\"}],\"keyClues\":[{\"content\":\"测试线索\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":1}],\"hostManual\":\"测试手册\"}";

        System.out.println("原始响应:");
        System.out.println(mockResponse);

        // 解析JSON
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> responseMap = objectMapper.readValue(mockResponse, java.util.Map.class);

        // 直接提取字段（调试模式方式）
        String progressTasks = responseMap.get("progressTasks").toString();
        String keyClues = responseMap.get("keyClues").toString();
        String hostManual = responseMap.get("hostManual").toString();

        System.out.println("\n提取的字段:");
        System.out.println("progressTasks: " + progressTasks);
        System.out.println("keyClues: " + keyClues);
        System.out.println("hostManual: " + hostManual);

        // 验证JSON格式
        try {
            objectMapper.readTree(progressTasks);
            System.out.println("\nprogressTasks 是有效的JSON");
        } catch (Exception e) {
            System.out.println("\nprogressTasks JSON格式错误: " + e.getMessage());
        }

        try {
            objectMapper.readTree(keyClues);
            System.out.println("keyClues 是有效的JSON");
        } catch (Exception e) {
            System.out.println("keyClues JSON格式错误: " + e.getMessage());
        }
    }

    /**
     * 测试转义字符处理
     */
    @Test
    public void testEscapeCharacters() {
        String textWithQuotes = "孙子给父母墓碑上尊称\"亚当\"和\"夏娃\"";
        System.out.println("原始文本: " + textWithQuotes);

        // 在JSON中的表示
        String jsonRepresentation = "{\"text\":\"" + textWithQuotes.replace("\"", "\\\"") + "\"}";
        System.out.println("JSON表示: " + jsonRepresentation);

        // 解析验证
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> parsed = objectMapper.readValue(jsonRepresentation, java.util.Map.class);
            String extractedText = parsed.get("text");
            System.out.println("解析后的文本: " + extractedText);
            System.out.println("文本是否匹配: " + textWithQuotes.equals(extractedText));
        } catch (Exception e) {
            System.out.println("解析失败: " + e.getMessage());
        }
    }
}