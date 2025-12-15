package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiSoupAuditRepository extends JpaRepository<HaiGuiSoupAudit, Long> {

    // 分页查询用户提交的海龟汤
    Page<HaiGuiSoupAudit> findByUploaderId(Long uploaderId, Pageable pageable);

    // 带条件的分页查询
    Page<HaiGuiSoupAudit> findByUploaderIdAndTitleContainingAndAuditStatus(
            Long uploaderId,
            String title,
            HaiGuiSoupAudit.AuditStatus status,
            Pageable pageable);
}
