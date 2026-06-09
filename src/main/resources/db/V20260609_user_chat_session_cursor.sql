-- 会话游标与清空记录边界（执行一次）
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，请只执行一次。
-- 若某列已存在会报 Duplicate column，跳过该句或整段忽略即可。

ALTER TABLE user_chat_session
  ADD COLUMN read_up_to_time DATETIME NULL COMMENT '已读游标时间',
  ADD COLUMN last_read_message_id VARCHAR(36) NULL COMMENT '已读游标消息ID',
  ADD COLUMN history_clear_at DATETIME NULL COMMENT '清空聊天记录边界时间',
  ADD COLUMN history_clear_message_id VARCHAR(36) NULL COMMENT '清空聊天记录边界消息ID';
