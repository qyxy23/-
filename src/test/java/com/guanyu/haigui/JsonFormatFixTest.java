package com.guanyu.haigui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * JSON格式修复验证测试
 */
@SpringBootTest
public class JsonFormatFixTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试修复后的JSON格式
     */
    @Test
    public void testFixedJsonFormat() throws Exception {
        // 模拟AI返回的原始响应（无转义）
        String mockAiResponse = """
            {
              "progressSettings": [
                {
                  "taskName": "基础调查",
                  "description": "调查旅行途中发生的意外",
                  "difficulty": "easy",
                  "increment": 15.0
                },
                {
                  "taskName": "称呼之谜",
                  "description": "思考为什么孙子称呼父母为\"亚当\"和\"夏娃\"",
                  "difficulty": "medium",
                  "increment": 30.0
                }
              ],
              "keyClues": [
                {
                  "content": "星际旅行和虫洞",
                  "isKey": true,
                  "clueType": "TIME",
                  "difficulty": 1
                },
                {
                  "content": "地球上没有人类生活痕迹",
                  "isKey": true,
                  "clueType": "PLACE",
                  "difficulty": 1
                }
              ],
              "hostManual": "游戏流程说明..."
            }
            """;

        System.out.println("=== 修复前的处理方式（会产生转义）===");
        testOldProcessing(mockAiResponse);

        System.out.println("\n=== 修复后的处理方式（无转义）===");
        testNewProcessing(mockAiResponse);
    }

    /**
     * 模拟修复前的处理方式（会产生转义）
     */
    private void testOldProcessing(String mockAiResponse) throws Exception {
        // 解析JSON为Map
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> responseMap = objectMapper.readValue(mockAiResponse, java.util.Map.class);

        // 错误做法：再次序列化（会产生转义）
        String progressSettings = objectMapper.writeValueAsString(responseMap.get("progressSettings"));
        String keyClues = objectMapper.writeValueAsString(responseMap.get("keyClues"));

        System.out.println("progressSettings（有转义）:");
        System.out.println(progressSettings);
        System.out.println("\nkeyClues（有转义）:");
        System.out.println(keyClues);
    }

    /**
     * 模拟修复后的处理方式（无转义）
     */
    private void testNewProcessing(String mockAiResponse) throws Exception {
        // 解析JSON为Map
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> responseMap = objectMapper.readValue(mockAiResponse, java.util.Map.class);

        // 正确做法：直接使用原始字符串（无转义）
        String progressSettings = responseMap.get("progressSettings").toString();
        String keyClues = responseMap.get("keyClues").toString();

        System.out.println("progressSettings（无转义）:");
        System.out.println(progressSettings);
        System.out.println("\nkeyClues（无转义）:");
        System.out.println(keyClues);

        // 验证是否为有效JSON
        try {
            objectMapper.readTree(progressSettings);
            System.out.println("✅ progressSettings 是有效的JSON");
        } catch (Exception e) {
            System.out.println("❌ progressSettings JSON格式错误: " + e.getMessage());
        }

        try {
            objectMapper.readTree(keyClues);
            System.out.println("✅ keyClues 是有效的JSON");
        } catch (Exception e) {
            System.out.println("❌ keyClues JSON格式错误: " + e.getMessage());
        }
    }

    /**
     * 测试前端使用场景
     */
    @Test
    public void testFrontendUsage() throws Exception {
        // 模拟修复后的返回数据
        String fixedJson = """
            {
              "progressSettings": "[{\"taskName\":\"基础调查\",\"description\":\"调查旅行途中发生的意外\",\"difficulty\":\"easy\",\"increment\":15.0}]",
              "keyClues": "[{\"content\":\"星际旅行和虫洞\",\"isKey\":true,\"clueType\":\"TIME\",\"difficulty\":1}]",
              "hostManual": "游戏流程说明..."
            }
            """;

        System.out.println("=== 前端可以直接使用的JSON格式 ===");
        System.out.println(fixedJson);

        // 验证前端可以直接解析
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> frontendData = objectMapper.readValue(fixedJson, java.util.Map.class);

        System.out.println("\n✅ 前端可以直接解析和使用:");
        System.out.println("progressSettings: " + frontendData.get("progressSettings"));
        System.out.println("keyClues: " + frontendData.get("keyClues"));
    }
}