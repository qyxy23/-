-- 海龟汤封面：上传者待审封面与审核状态
ALTER TABLE `hai_gui_soup`
    ADD COLUMN `pending_cover_url` VARCHAR(512) DEFAULT NULL COMMENT '待人工复核的封面 URL' AFTER `soup_avatar`,
    ADD COLUMN `cover_audit_status` VARCHAR(20) NOT NULL DEFAULT 'NONE'
        COMMENT '封面上传审核状态：NONE/PENDING_REVIEW/REJECTED' AFTER `pending_cover_url`;
