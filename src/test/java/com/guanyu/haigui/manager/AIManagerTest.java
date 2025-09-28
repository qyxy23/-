package com.guanyu.haigui.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AIManagerTest {

    @Resource
    private AIManager aiManager;

    @Test
    void doChat() {
        String result = "你是一个程序员大佬";
        String question = "写一个用Java输出hello的程序";
        String answer = aiManager.doChat(result,question);
        System.out.println(answer);
    }
}