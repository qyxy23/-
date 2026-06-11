-- 性能优化：补充高频查询复合索引
-- 执行一次；若某索引已存在，跳过对应 CREATE 语句即可。
-- 建议在低峰期执行；大表建索引可能锁表数秒～数分钟。
-- 依赖：V20260610_play_quota.sql（quota_charged 列）已执行。

-- =============================================================================
-- P0 热路径
-- =============================================================================

-- 群/私聊会话：按 session_id + chat_type 更新未读、置顶
CREATE INDEX `idx_session_type` ON `user_chat_session` (`session_id`, `chat_type`);

-- 大厅聊天消息分页
CREATE INDEX `idx_room_time` ON `chat_game_messages` (`room_id`, `create_time`);

-- 群聊消息分页与增量同步
CREATE INDEX `idx_group_time` ON `chat_group_messages` (`group_id`, `create_time`);

-- 用户游戏会话列表（含 is_deleted 过滤 + 时间排序）
CREATE INDEX `idx_user_del_status_time` ON `haigui_game_session` (`user_id`, `is_deleted`, `status`, `start_time`);

-- 单人/多人续局、同汤重复开局校验
CREATE INDEX `idx_soup_user_del_time` ON `haigui_game_session` (`soup_id`, `user_id`, `is_deleted`, `start_time`);

-- 游戏记录：单人已结束列表
CREATE INDEX `idx_user_mode_del_end` ON `haigui_game_session` (`user_id`, `play_mode`, `is_deleted`, `end_time`);

-- 汤列表：已发布 + 未删除 + 上传时间排序
CREATE INDEX `idx_pub_del_upload` ON `hai_gui_soup` (`is_published`, `is_deleted`, `upload_time`);

-- 登录查询（执行前请确认 username/phone/email 无重复非 NULL 值）
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_username` (`username`);
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`);
ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_email` (`email`);

-- =============================================================================
-- P1 明显缺口
-- =============================================================================

-- AI 问答历史（按会话 + 时间）
CREATE INDEX `idx_session_created` ON `hai_gui_chat_message_with_fragments` (`game_session_id`, `created_at`);

-- 投票：进行中会话、超时扫描
CREATE INDEX `idx_session_status_created` ON `hai_gui_vote_session` (`session_id`, `status`, `created_at`);
CREATE INDEX `idx_status_end` ON `hai_gui_vote_session` (`status`, `end_time`);

-- 房间邀请校验
CREATE INDEX `idx_room_invitee_status` ON `chat_game_invitations` (`room_id`, `invitee_id`, `status`);

-- AI 单人对话列表
CREATE INDEX `idx_user_del_update` ON `ai_chat_sessions` (`user_id`, `is_deleted`, `update_time`);

-- 我的游玩申请记录
CREATE INDEX `idx_user_create` ON `play_access_request` (`user_id`, `create_time`);

-- 额度统计：进行中未扣次局数
CREATE INDEX `idx_user_status_quota` ON `haigui_game_session` (`user_id`, `status`, `quota_charged`, `is_deleted`);

-- 线索/任务加载（Settlement、提问 pipeline）
CREATE INDEX `idx_soup_del` ON `hai_gui_soup_clue_fragment` (`soup_id`, `is_deleted`);
CREATE INDEX `idx_soup_del_order` ON `hai_gui_soup_inference_task` (`soup_id`, `is_deleted`, `task_order`);

-- 大厅成员列表、按状态排序
CREATE INDEX `idx_room_status_join` ON `chat_game_members` (`room_id`, `status`, `join_time`);

-- 大厅搜索分页
CREATE INDEX `idx_status_create` ON `chat_games` (`status`, `create_time`);

-- 好友申请列表
CREATE INDEX `idx_user_status_apply` ON `friend_relations` (`user_id`, `status`, `apply_time`);
CREATE INDEX `idx_friend_status_apply` ON `friend_relations` (`friend_id`, `status`, `apply_time`);

-- 审核列表
CREATE INDEX `idx_uploader_status` ON `hai_gui_soup_audit` (`uploader_id`, `audit_status`);
CREATE INDEX `idx_audit_status_created` ON `hai_gui_soup_audit` (`audit_status`, `created_at`);

-- =============================================================================
-- P2 可选：冗余索引清理（确认无慢查询依赖后再执行）
-- =============================================================================
-- ALTER TABLE `hai_gui_room_progress` DROP INDEX `idx_game_session_id`;
-- ALTER TABLE `hai_gui_vote_record` DROP INDEX `idx_vote_session_id`;
-- ALTER TABLE `chat_game_messages` DROP INDEX `idx_room_id`;
-- ALTER TABLE `chat_group_messages` DROP INDEX `idx_group_id`;
-- ALTER TABLE `hai_gui_chat_message_with_fragments` DROP INDEX `idx_game_session_id`;
