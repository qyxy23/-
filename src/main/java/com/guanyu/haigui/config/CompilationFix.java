package com.guanyu.haigui.config;

import com.guanyu.haigui.utils.RedisStackClient;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.stereotype.Component;

/**
 * 编译问题修复验证
 * 验证RedisStackClient.getCommands()方法是否正常工作
 */
@Component
public class CompilationFix {

    private final RedisStackClient redisStackClient;

    public CompilationFix(RedisStackClient redisStackClient) {
        this.redisStackClient = redisStackClient;
        validateGetCommandsMethod();
    }

    /**
     * 验证getCommands方法是否可用
     */
    private void validateGetCommandsMethod() {
        try {
            RedisCommands<String, String> commands = redisStackClient.getCommands();
            System.out.println("✅ RedisStackClient.getCommands() 方法正常工作");
        } catch (Exception e) {
            System.err.println("❌ RedisStackClient.getCommands() 方法存在问题: " + e.getMessage());
        }
    }
}