package com.guanyu.haigui;

import com.guanyu.haigui.pojo.dto.TurtleSoupEnhanceDTO;
import com.guanyu.haigui.pojo.vo.TurtleSoupEnhanceResultVO;
import com.guanyu.haigui.service.ServicesImpl.haiGuiTangServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 海龟汤AI增强功能测试
 */
@SpringBootTest
public class TurtleSoupEnhanceTest {

    @Autowired
    private haiGuiTangServiceImpl haiGuiTangService;

    /**
     * 测试情况1：只有汤面和汤底
     */
    @Test
    public void testType1_SurfaceBottomOnly() {
        TurtleSoupEnhanceDTO dto = new TurtleSoupEnhanceDTO();
        dto.setSoupTitle("双鱼玉佩");
        dto.setSoupSurface("一位探险家在新疆沙漠中发现了双鱼玉佩，这块玉佩似乎有复制生物的能力...");
        dto.setSoupBottom("双鱼玉佩是一个外星文明的生物复制装置，探险家被复制后出现了两个相同的自己...");

        TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(dto);
        System.out.println("测试类型1结果:");
        System.out.println("状态: " + result.getStatus());
        System.out.println("Prompt类型: " + result.getPromptType());
        System.out.println("生成的进度任务: " + result.getProgressTasks());
        System.out.println("生成的关键线索: " + result.getKeyClues());
        System.out.println("生成的主持人手册: " + result.getHostManual());
    }

    /**
     * 测试情况2：汤面、汤底、进度任务都有
     */
    @Test
    public void testType2_WithProgressTasks() {
        TurtleSoupEnhanceDTO dto = new TurtleSoupEnhanceDTO();
        dto.setSoupTitle("教室幽灵");
        dto.setSoupSurface("深夜的教室里总会传来奇怪的哭声，有人说看到一个穿着校服的幽灵...");
        dto.setSoupBottom("其实是一个清洁工的女儿，她因为思念去世的母亲而经常晚上来教室看母亲留下的物品...");

        // 提供进度任务
        String progressTasks = "[{\"taskName\":\"调查哭声来源\",\"description\":\"找出哭声的具体位置和时间\",\"points\":10,\"difficulty\":\"easy\"}," +
                "{\"taskName\":\"寻找目击者\",\"description\":\"采访声称看到幽灵的学生\",\"points\":20,\"difficulty\":\"medium\"}]";
        dto.setProgressTasks(progressTasks);

        TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(dto);
        System.out.println("测试类型2结果:");
        System.out.println("状态: " + result.getStatus());
        System.out.println("Prompt类型: " + result.getPromptType());
        System.out.println("保留的进度任务: " + result.getProgressTasks());
        System.out.println("生成的关键线索: " + result.getKeyClues());
        System.out.println("生成的主持人手册: " + result.getHostManual());
    }

    /**
     * 测试情况3：汤面、汤底、关键线索都有
     */
    @Test
    public void testType3_WithKeyClues() {
        TurtleSoupEnhanceDTO dto = new TurtleSoupEnhanceDTO();
        dto.setSoupTitle("消失的时间");
        dto.setSoupSurface("小李每天晚上8点后都会忘记接下来发生的事情，第二天醒来发现自己完成了许多工作...");
        dto.setSoupBottom("小李患有梦游症，晚上在无意识状态下完成工作，但对此没有记忆...");

        // 提供关键线索
        String keyClues = "[{\"content\":\"小李的办公桌上总有未知的文件\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":1}," +
                "{\"content\":\"室友说小李晚上会起来走动\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":2}]";
        dto.setKeyClues(keyClues);

        TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(dto);
        System.out.println("测试类型3结果:");
        System.out.println("状态: " + result.getStatus());
        System.out.println("Prompt类型: " + result.getPromptType());
        System.out.println("生成的进度任务: " + result.getProgressTasks());
        System.out.println("保留的关键线索: " + result.getKeyClues());
        System.out.println("生成的主持人手册: " + result.getHostManual());
    }

    /**
     * 测试情况8：全部信息都有（优化）
     */
    @Test
    public void testType8_CompleteOptimization() {
        TurtleSoupEnhanceDTO dto = new TurtleSoupEnhanceDTO();
        dto.setSoupTitle("神秘邻居");
        dto.setSoupSurface("新邻居从不出门，但每天晚上都能听到他家传来音乐声...");
        dto.setSoupBottom("新邻居是一位盲人音乐家，因为自卑而很少出门，晚上是他的创作时间...");

        // 提供所有信息
        String progressTasks = "[{\"taskName\":\"观察邻居作息\",\"description\":\"记录邻居的活动规律\",\"points\":5,\"difficulty\":\"easy\"}]";
        String keyClues = "[{\"content\":\"门口有盲道标识\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":1}]";
        String hostManual = "这是一个关于理解和包容的故事...";

        dto.setProgressTasks(progressTasks);
        dto.setKeyClues(keyClues);
        dto.setHostManual(hostManual);

        TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(dto);
        System.out.println("测试类型8结果:");
        System.out.println("状态: " + result.getStatus());
        System.out.println("Prompt类型: " + result.getPromptType());
        System.out.println("优化后的标题: " + result.getEnhancedTitle());
        System.out.println("优化后的进度任务: " + result.getProgressTasks());
        System.out.println("优化后的关键线索: " + result.getKeyClues());
        System.out.println("优化后的主持人手册: " + result.getHostManual());
    }
}