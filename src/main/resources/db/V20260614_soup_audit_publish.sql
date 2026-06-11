-- 审核表：异步发布状态字段（代码依赖 publish_status / publish_error / publish_updated_at）
-- 若已执行过 sql/migration_20260606_publish_async.sql 可跳过

SET NAMES utf8mb4;

ALTER TABLE `hai_gui_soup_audit`
  ADD COLUMN `publish_status` VARCHAR(20) NOT NULL DEFAULT 'IDLE'
    COMMENT '发布状态: IDLE/PUBLISHING/SUCCESS/FAILED' AFTER `ai_gen_updated_at`;

ALTER TABLE `hai_gui_soup_audit`
  ADD COLUMN `publish_error` TEXT NULL COMMENT '发布失败原因' AFTER `publish_status`;

ALTER TABLE `hai_gui_soup_audit`
  ADD COLUMN `publish_updated_at` DATETIME(6) NULL COMMENT '发布状态更新时间' AFTER `publish_error`;
