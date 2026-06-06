package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "haiqutang.vector")
public class VectorSearchProperties {

    /**
     * RediSearch 索引名称
     */
    private String indexName = "idx:clue_fragment";

    /**
     * 线索向量 Hash 键前缀
     */
    private String keyPrefix = "hai_gui:vec:clue:";

    /**
     * BGE 向量维度
     */
    private int dimensions = 512;

    /**
     * 启动时是否检查 Redis 并补齐缺失的线索向量（已有数据不重复写入）
     */
    private boolean autoMigrate = true;
}
