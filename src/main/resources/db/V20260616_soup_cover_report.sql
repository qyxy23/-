-- 海龟汤封面举报
CREATE TABLE IF NOT EXISTS `soup_cover_report` (
    `report_id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '举报 ID',
    `soup_id`        VARCHAR(36)     NOT NULL COMMENT '海龟汤 ID',
    `reporter_id`    BIGINT UNSIGNED NOT NULL COMMENT '举报人 ID',
    `cover_url`      VARCHAR(512)    NOT NULL COMMENT '举报时的封面 URL 快照',
    `reason_type`    VARCHAR(32)     NOT NULL COMMENT '举报原因类型',
    `reason_detail`  VARCHAR(500)             DEFAULT NULL COMMENT '补充说明',
    `status`         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISMISSED/COVER_REMOVED',
    `handler_id`     BIGINT UNSIGNED          DEFAULT NULL COMMENT '处理人 ID',
    `handle_note`    VARCHAR(500)             DEFAULT NULL COMMENT '处理备注',
    `created_at`     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `handled_at`     DATETIME(6)              DEFAULT NULL,
    PRIMARY KEY (`report_id`),
    KEY `idx_soup_status` (`soup_id`, `status`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_reporter_pending` (`reporter_id`, `soup_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='海龟汤封面举报';
