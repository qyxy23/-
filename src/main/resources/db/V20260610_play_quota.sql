-- 游玩额度与申请审批（Phase 0：审批制，不接支付）
-- 执行一次；若列/表已存在请跳过对应语句。

-- 1. 游戏会话：记录是否已扣减额度（结算见汤底时扣减，幂等）
ALTER TABLE `haigui_game_session`
  ADD COLUMN `quota_charged` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已扣减游玩额度';

-- 历史已结束对局标记为已扣减，避免迁移后重复扣减
UPDATE `haigui_game_session`
SET `quota_charged` = 1
WHERE `status` IN ('COMPLETED', 'CANCELED');

-- 2. 用户游玩额度汇总
CREATE TABLE IF NOT EXISTS `user_play_quota` (
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `global_games_remaining` int NOT NULL DEFAULT '0' COMMENT '通用局数余额',
  `unlimited` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否无限游玩（管理员/审核员等）',
  `total_consumed` int NOT NULL DEFAULT '0' COMMENT '累计消耗局数',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_play_quota_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户游玩额度';

-- 3. 游玩申请（额度用尽后向开发者/审核员申请）
CREATE TABLE IF NOT EXISTS `play_access_request` (
  `request_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '申请人',
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `user_message` text COMMENT '用户留言',
  `admin_note` text COMMENT '审核备注',
  `reviewer_id` bigint unsigned DEFAULT NULL COMMENT '审核人',
  `granted_games` int DEFAULT NULL COMMENT '批准赠送局数',
  `reviewed_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`request_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_status_time` (`status`, `create_time`),
  CONSTRAINT `fk_play_access_request_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_play_access_request_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游玩额度申请';

-- 4. 用户权益（预留：按汤解锁 / 订阅，Phase 1 支付接入后使用）
CREATE TABLE IF NOT EXISTS `user_entitlement` (
  `entitlement_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `entitlement_type` enum('GLOBAL_GAME','SINGLE_SOUP','SUBSCRIPTION') NOT NULL,
  `soup_id` varchar(36) DEFAULT NULL COMMENT 'SINGLE_SOUP 时关联汤ID',
  `quantity_remaining` int DEFAULT NULL COMMENT '剩余次数，NULL 表示不限次',
  `valid_until` datetime(6) DEFAULT NULL COMMENT '订阅到期时间',
  `source` enum('REGISTER','ADMIN_GRANT','PURCHASE','APPROVAL') NOT NULL,
  `source_ref_id` bigint unsigned DEFAULT NULL COMMENT '来源关联ID，如 request_id',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`entitlement_id`),
  KEY `idx_user_type` (`user_id`, `entitlement_type`),
  KEY `idx_user_soup` (`user_id`, `soup_id`),
  CONSTRAINT `fk_user_entitlement_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户权益（通用局数/单汤/订阅）';

-- 5. 额度变更流水
CREATE TABLE IF NOT EXISTS `play_quota_ledger` (
  `ledger_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `delta` int NOT NULL COMMENT '正数增加，负数扣减',
  `balance_after` int NOT NULL COMMENT '变更后 global_games_remaining',
  `reason` varchar(64) NOT NULL COMMENT 'REGISTER/APPROVAL/CONSUME/ADMIN_GRANT 等',
  `game_session_id` varchar(36) DEFAULT NULL,
  `source_ref_id` bigint unsigned DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`ledger_id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_play_quota_ledger_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游玩额度流水';
