package com.guanyu.haigui.pojo.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_play_quota")
public class UserPlayQuota {

    @Id
    @Column(name = "user_id", columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(name = "global_games_remaining", nullable = false)
    private Integer globalGamesRemaining = 0;

    @Column(name = "unlimited", nullable = false)
    private Boolean unlimited = false;

    @Column(name = "total_consumed", nullable = false)
    private Integer totalConsumed = 0;

    @CreationTimestamp
    @Column(name = "create_time", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", columnDefinition = "DATETIME(6)", nullable = false)
    private LocalDateTime updateTime;
}
