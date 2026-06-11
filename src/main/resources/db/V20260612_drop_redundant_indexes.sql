-- =============================================================================
-- P2 可选：冗余索引清理（请先执行 V20260611，观察 1～2 天后再执行本文件）
-- 若 DROP 报错「索引不存在」，跳过对应语句即可。
-- =============================================================================
ALTER TABLE `hai_gui_room_progress` DROP INDEX `idx_game_session_id`;

-- uk_vote_session_user 已覆盖 vote_session_id 左前缀
ALTER TABLE `hai_gui_vote_record` DROP INDEX `idx_vote_session_id`;

-- idx_room_time / idx_group_time 已覆盖 room_id / group_id 左前缀
ALTER TABLE `chat_game_messages` DROP INDEX `idx_room_id`;
ALTER TABLE `chat_group_messages` DROP INDEX `idx_group_id`;

-- idx_session_created 已覆盖 game_session_id 左前缀
ALTER TABLE `hai_gui_chat_message_with_fragments` DROP INDEX `idx_game_session_id`;
