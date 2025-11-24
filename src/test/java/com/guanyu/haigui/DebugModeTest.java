package com.guanyu.haigui;

import com.guanyu.haigui.pojo.dto.TurtleSoupEnhanceDTO;
import com.guanyu.haigui.pojo.vo.TurtleSoupEnhanceResultVO;
import com.guanyu.haigui.service.ServicesImpl.haiGuiTangServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 调试模式测试
 */
@SpringBootTest
public class DebugModeTest {

    @Autowired
    private haiGuiTangServiceImpl haiGuiTangService;

    /**
     * 测试调试模式下的AI增强功能
     */
    @Test
    public void testDebugMode() {
        TurtleSoupEnhanceDTO dto = new TurtleSoupEnhanceDTO();
        dto.setSoupTitle("《墓碑上的文字》");
        dto.setSoupSurface("一家人去星际旅行，回来后父母却打算要二胎。五十年后，孙子在父母的墓碑前祭祀，却尊称他们为'亚当'和'夏娃'。");
        dto.setSoupBottom("他们意外穿越到了史前地球，成为了人类的祖先。父母去世后，被尊称为人类的始祖亚当和夏娃。");

        TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(dto);

        System.out.println("=== 调试模式测试结果 ===");
        System.out.println("状态: " + result.getStatus());
        System.out.println("Prompt类型: " + result.getPromptType());
        System.out.println("进度任务数量: " + (result.getProgressTasks() != null ? "已生成" : "未生成"));
        System.out.println("关键线索数量: " + (result.getKeyClues() != null ? "已生成" : "未生成"));
        System.out.println("主持人手册: " + (result.getHostManual() != null ? "已生成" : "未生成"));

        if (result.getProgressTasks() != null) {
            System.out.println("\n=== 进度任务示例 ===");
            System.out.println(result.getProgressTasks().substring(0, Math.min(200, result.getProgressTasks().length())) + "...");
        }

        if (result.getKeyClues() != null) {
            System.out.println("\n=== 关键线索示例 ===");
            System.out.println(result.getKeyClues().substring(0, Math.min(200, result.getKeyClues().length())) + "...");
        }
    }
}