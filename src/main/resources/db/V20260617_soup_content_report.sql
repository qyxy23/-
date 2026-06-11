-- 海龟汤内容举报
CREATE TABLE IF NOT EXISTS `soup_content_report` (
    `report_id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `soup_id`        VARCHAR(36)     NOT NULL,
    `reporter_id`    BIGINT UNSIGNED NOT NULL,
    `reason_type`    VARCHAR(32)     NOT NULL,
    `reason_detail`  VARCHAR(500)             DEFAULT NULL,
    `status`         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    `handler_id`     BIGINT UNSIGNED          DEFAULT NULL,
    `handle_note`    VARCHAR(500)             DEFAULT NULL,
    `created_at`     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `handled_at`     DATETIME(6)              DEFAULT NULL,
    PRIMARY KEY (`report_id`),
    KEY `idx_soup_status` (`soup_id`, `status`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_reporter_pending` (`reporter_id`, `soup_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
