package com.guanyu.haigui.vector;

import com.guanyu.haigui.config.VectorSearchProperties;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时检查 Redis Stack，仅将 MySQL 中缺失的线索向量补齐到索引
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VectorMigrationRunner implements ApplicationRunner {

    private final VectorSearchProperties properties;
    private final ClueFragmentRepository clueFragmentRepository;
    private final RedisStackClient redisStackClient;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoMigrate()) {
            log.info("向量自动迁移已关闭 (haiqutang.vector.auto-migrate=false)");
            return;
        }

        List<ClueFragment> fragments = clueFragmentRepository.findByIsDeletedFalse();
        int migrated = 0;
        int alreadyPresent = 0;
        int skipped = 0;
        Set<String> soupIds = new HashSet<>();

        for (ClueFragment fragment : fragments) {
            if (!StringUtils.hasText(fragment.getFragmentContent())) {
                skipped++;
                continue;
            }

            String soupId = fragment.getSoupId();
            String fragmentId = fragment.getFragmentId().toString();
            if (redisStackClient.clueFragmentVectorExists(soupId, fragmentId)) {
                alreadyPresent++;
                continue;
            }

            List<Double> vector = resolveVector(fragment);
            if (vector == null || vector.isEmpty()) {
                skipped++;
                continue;
            }

            boolean stored = redisStackClient.storeClueFragmentVector(soupId, fragmentId, vector);
            if (stored) {
                redisStackClient.registerClueFragment(soupId, fragmentId);
                soupIds.add(soupId);
                migrated++;
            }
        }

        if (migrated == 0) {
            log.info("线索向量检查完成: total={}, alreadyPresent={}, skipped={}, 无需补齐",
                    fragments.size(), alreadyPresent, skipped);
        } else {
            log.info("线索向量迁移完成: total={}, migrated={}, alreadyPresent={}, skipped={}, soups={}",
                    fragments.size(), migrated, alreadyPresent, skipped, soupIds.size());
        }
    }

    private List<Double> resolveVector(ClueFragment fragment) {
        try {
            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(fragment.getFragmentContent());
            if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                return null;
            }
            List<Float> embeddings = response.getEmbeddings().get(0);
            List<Double> vector = new ArrayList<>(embeddings.size());
            for (Float value : embeddings) {
                vector.add(value.doubleValue());
            }
            return vector;
        } catch (Exception e) {
            log.warn("重新向量化失败: fragmentId={}, soupId={}", fragment.getFragmentId(), fragment.getSoupId(), e);
            return null;
        }
    }
}
