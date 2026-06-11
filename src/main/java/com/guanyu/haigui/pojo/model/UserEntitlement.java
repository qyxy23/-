package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.EntitlementSource;
import com.guanyu.haigui.Enum.EntitlementType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_entitlement",
        indexes = {
                @Index(name = "idx_user_type", columnList = "user_id, entitlement_type"),
                @Index(name = "idx_user_soup", columnList = "user_id, soup_id")
        })
public class UserEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entitlement_id", columnDefinition = "BIGINT UNSIGNED")
    private Long entitlementId;

    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_type", columnDefinition = "ENUM('GLOBAL_GAME','SINGLE_SOUP','SUBSCRIPTION')", nullable = false)
    private EntitlementType entitlementType;

    @Column(name = "soup_id", columnDefinition = "VARCHAR(36)")
    private String soupId;

    @Column(name = "quantity_remaining")
    private Integer quantityRemaining;

    @Column(name = "valid_until", columnDefinition = "DATETIME(6)")
    private LocalDateTime validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", columnDefinition = "ENUM('REGISTER','ADMIN_GRANT','PURCHASE','APPROVAL')", nullable = false)
    private EntitlementSource source;

    @Column(name = "source_ref_id", columnDefinition = "BIGINT UNSIGNED")
    private Long sourceRefId;

    @CreationTimestamp
    @Column(name = "create_time", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime updateTime;
}
