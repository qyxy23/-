package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.CoverAiGenStatusVO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 海龟汤封面 AI 生成：按 soupId 分布式锁 + Redis 生成中状态（支持离开页面后恢复展示）
 */
@Service
@RequiredArgsConstructor
public class SoupCoverAiGenStatusService {

    static final String LOCK_PREFIX = "soup:cover_ai_generate:";
    private static final String STATUS_PREFIX = "soup:cover_ai_gen_status:";
    static final long LOCK_LEASE_MINUTES = 3;
    private static final long STATUS_TTL_MINUTES = 3;

    private final RedissonClient redissonClient;

    public RLock getLock(String soupId) {
        return redissonClient.getLock(LOCK_PREFIX + soupId);
    }

    public void markGenerating(String soupId, Long userId) {
        RBucket<String> bucket = redissonClient.getBucket(STATUS_PREFIX + soupId);
        bucket.set(String.valueOf(userId), STATUS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public void clearGenerating(String soupId) {
        redissonClient.getBucket(STATUS_PREFIX + soupId).delete();
    }

    public CoverAiGenStatusVO resolveStatus(String soupId, Long currentUserId) {
        RLock lock = getLock(soupId);
        RBucket<String> bucket = redissonClient.getBucket(STATUS_PREFIX + soupId);
        String stored = bucket.get();

        boolean lockHeld = lock.isLocked();
        if (!lockHeld && StringUtils.hasText(stored)) {
            bucket.delete();
            stored = null;
        }

        boolean generating = lockHeld || StringUtils.hasText(stored);
        Long generatingUserId = null;
        if (StringUtils.hasText(stored)) {
            try {
                generatingUserId = Long.parseLong(stored);
            } catch (NumberFormatException ignored) {
                bucket.delete();
                generating = lockHeld;
            }
        }

        boolean byMe = currentUserId != null && currentUserId.equals(generatingUserId);
        return CoverAiGenStatusVO.builder()
                .generating(generating)
                .generatingUserId(generatingUserId)
                .generatingByMe(byMe)
                .build();
    }
}
