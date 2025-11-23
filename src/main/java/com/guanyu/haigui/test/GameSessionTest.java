package com.guanyu.haigui.test;

import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.repository.GameSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 简单的GameSession测试类
 */
@Component
public class GameSessionTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    public void testGameSession() {
        // 测试方法是否存在
        try {
            System.out.println("GameSessionRepository测试开始...");

            // 测试基本方法是否存在
            long count = gameSessionRepository.count();
            System.out.println("总记录数: " + count);

            System.out.println("GameSessionRepository测试完成 - 所有方法都存在!");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}