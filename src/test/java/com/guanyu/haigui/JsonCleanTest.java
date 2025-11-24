package com.guanyu.haigui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * JSON清理功能测试
 */
@SpringBootTest
public class JsonCleanTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试包含换行符的JSON清理
     */
    @Test
    public void testJsonCleanWithNewlines() {
        // 模拟AI返回的包含换行符的响应
        String aiResponse = """
            {
              "progressTasks": [
                {
                  "taskName": "旅行归来的疑惑",
                  "description": "根据汤面，推测旅行途中可能发生了什么意外导致父母打算要二胎",
                  "points": 10,
                  "difficulty": "easy"
                }
              ],
              "keyClues": [
                {
                  "content": "星际旅行和虫洞",
                  "isKey": true,
                  "clueType": "main",
                  "difficulty": 1
                }
              ],
              "hostManual": "游戏流程：主持人向玩家讲述汤面..."
            }
            """;

        System.out.println("原始AI响应:");
        System.out.println(aiResponse);
        System.out.println("\n原始长度: " + aiResponse.length());

        try {
            // 尝试直接解析
            objectMapper.readTree(aiResponse);
            System.out.println("直接解析成功！");
        } catch (Exception e) {
            System.out.println("直接解析失败: " + e.getMessage());
        }

        // 简单清理
        String cleaned = aiResponse.replaceAll("\\r\\n", " ").replaceAll("\\n", " ").replaceAll("\\r", " ");
        System.out.println("\n清理后长度: " + cleaned.length());

        try {
            objectMapper.readTree(cleaned);
            System.out.println("清理后解析成功！");
        } catch (Exception e) {
            System.out.println("清理后解析失败: " + e.getMessage());
        }
    }

    /**
     * 测试提取JSON功能
     */
    @Test
    public void testExtractJson() {
        String responseWithExtraText = """
            根据您的要求，我生成了以下海龟汤内容：

            {"progressTasks":[{"taskName":"测试任务","description":"这是一个测试","points":10,"difficulty":"easy"}],"keyClues":[],"hostManual":"测试手册"}

            希望这些内容对您有帮助！
            """;

        System.out.println("包含额外文本的响应:");
        System.out.println(responseWithExtraText);

        int start = responseWithExtraText.indexOf("{");
        int end = responseWithExtraText.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            String extracted = responseWithExtraText.substring(start, end + 1);
            System.out.println("\n提取的JSON:");
            System.out.println(extracted);

            try {
                objectMapper.readTree(extracted);
                System.out.println("提取的JSON解析成功！");
            } catch (Exception e) {
                System.out.println("提取的JSON解析失败: " + e.getMessage());
            }
        }
    }
}