package com.guanyu.haigui.service;

import com.guanyu.haigui.service.ServicesImpl.HaiGuiRankingService;
import com.guanyu.haigui.utils.RedisStackClient;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 海龟汤榜单服务测试类
 */
@ExtendWith(MockitoExtension.class)
class HaiGuiRankingServiceTest {

    @Mock
    private RedisStackClient redisStackClient;

    @Mock
    private RedisCommands<String, String> commands;

    @InjectMocks
    private HaiGuiRankingService haiGuiRankingService;

    @Test
    void testGetCommandsAccess() {
        // 测试能否正常访问RedisCommands
        when(redisStackClient.getCommands()).thenReturn(commands);

        RedisCommands<String, String> result = redisStackClient.getCommands();

        assertNotNull(result);
        assertEquals(commands, result);
    }

    @Test
    void testRecordUserAction() {
        // 准备测试数据
        String soupId = "test-soup-001";
        Long userId = 123L;
        String action = "play";

        when(redisStackClient.getCommands()).thenReturn(commands);
        when(commands.hincrby(anyString(), anyString(), eq(1L))).thenReturn(1L);

        // 执行测试
        assertDoesNotThrow(() -> {
            haiGuiRankingService.recordUserAction(soupId, userId, action);
        });

        // 验证调用
        verify(redisStackClient, times(1)).getCommands();
        verify(commands, times(1)).hincrby(anyString(), anyString(), eq(1L));
    }

    @Test
    void testGetSoupById() throws Exception {
        // 准备测试数据
        String soupId = "test-soup-001";
        Map<String, String> soupData = new HashMap<>();
        soupData.put("soupTitle", "测试标题");
        soupData.put("soupSurface", "测试汤面");
        soupData.put("soupBottom", "测试汤底");
        soupData.put("playCount", "100");
        soupData.put("createdAt", "1642521600000");

        when(redisStackClient.soupExists(soupId)).thenReturn(true);
        when(redisStackClient.getSoupInfo(soupId)).thenReturn(soupData);

        // 使用反射调用私有方法
        java.lang.reflect.Method method = HaiGuiRankingService.class.getDeclaredMethod("getSoupById", String.class);
        method.setAccessible(true);

        // 执行测试
        Object result = method.invoke(haiGuiRankingService, soupId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result instanceof com.guanyu.haigui.pojo.model.HaiGuiSoup);

        com.guanyu.haigui.pojo.model.HaiGuiSoup soup =
            (com.guanyu.haigui.pojo.model.HaiGuiSoup) result;
        assertEquals("测试标题", soup.getSoupTitle());
        assertEquals("测试汤面", soup.getSoupSurface());
        assertEquals(Integer.valueOf(100), soup.getPlayCount());

        verify(redisStackClient, times(1)).soupExists(soupId);
        verify(redisStackClient, times(1)).getSoupInfo(soupId);
    }

    @Test
    void testGetSoupById_NotExists() throws Exception {
        // 准备测试数据
        String soupId = "non-existent-soup";

        when(redisStackClient.soupExists(soupId)).thenReturn(false);

        // 使用反射调用私有方法
        java.lang.reflect.Method method = HaiGuiRankingService.class.getDeclaredMethod("getSoupById", String.class);
        method.setAccessible(true);

        // 执行测试
        Object result = method.invoke(haiGuiRankingService, soupId);

        // 验证结果
        assertNull(result);
        verify(redisStackClient, times(1)).soupExists(soupId);
        verify(redisStackClient, never()).getSoupInfo(soupId);
    }
}