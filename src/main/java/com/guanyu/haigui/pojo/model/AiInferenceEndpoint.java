package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * AI推理接入点配置表（存储火山引擎模型接入点元数据）
 * 对应数据库表：ai_inference_endpoint
 */
@Entity
@Table(
    name = "ai_inference_endpoint",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_endpoint_id", columnNames = "endpoint_id"), // 对应SQL的UNIQUE KEY uk_endpoint_id
    }
)
@Data // Lombok注解：自动生成getter/setter/toString等（需引入lombok依赖）
public class AiInferenceEndpoint {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键策略（MySQL AUTO_INCREMENT）
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 火山引擎推理接入点ID（格式：ep-xxx，调用模型时必填）
     */
    @Column(
        name = "endpoint_id",
        nullable = false,
        length = 100,
        unique = true, // 对应SQL的UNIQUE KEY uk_endpoint_id
        columnDefinition = "varchar(100) NOT NULL COMMENT '火山引擎推理接入点ID（格式：ep-xxx，调用模型时必填）'"
    )
    private String endpointId;

    /**
     * 模型名称（可读性标识，如“豆包-快速思考v1”）
     */
    @Column(
        name = "model_name",
        nullable = false,
        length = 50,
        columnDefinition = "varchar(50) NOT NULL COMMENT '模型名称（可读性标识，如“豆包-快速思考v1”）'"
    )
    private String modelName;

    /**
     * 业务唯一标识（如default_chat/deep_think，用于代码中指定模型）
     */
    @Column(
        name = "model_key",
        nullable = false,
        length = 50,
        unique = true, // 对应SQL的UNIQUE KEY uk_model_key
        columnDefinition = "varchar(50) NOT NULL COMMENT '业务唯一标识（如default_chat/deep_think，用于代码中指定模型）'"
    )
    private String modelKey;

    /**
     * 接入点描述（用途说明，如“默认对话模型，支持日常问答”）
     */
    @Column(
        name = "description",
            columnDefinition = "varchar(255) DEFAULT NULL COMMENT '接入点描述（用途说明，如“默认对话模型，支持日常问答”）'"
    )
    private String description;

    /**
     * 是否启用（1=启用，0=禁用，避免调用无效接入点）
     */
    @Column(
        name = "is_active",
        nullable = false,
        columnDefinition = "tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用（1=启用，0=禁用，避免调用无效接入点）'"
    )
    private Boolean isActive = false; // 默认0（禁用）

    /**
     * 创建时间
     */
    @CreationTimestamp // Hibernate注解：插入时自动填充当前时间
    @Column(
        name = "created_time",
        nullable = false,
        columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'"
    )
    private LocalDateTime createdTime;

    /**
     * 更新时间（自动更新）
     */
    @UpdateTimestamp // Hibernate注解：更新时自动填充当前时间
    @Column(
        name = "updated_time",
        nullable = false,
        columnDefinition = "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（自动更新）'"
    )
    private LocalDateTime updatedTime;
}