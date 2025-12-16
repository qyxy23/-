package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiSoupAuditRepository extends JpaRepository<HaiGuiSoupAudit, Long> {

    // 基础分页查询
    Page<HaiGuiSoupAudit> findByUploaderId(Long uploaderId, Pageable pageable);

    // 按标题模糊查询
    Page<HaiGuiSoupAudit> findByUploaderIdAndTitleContaining(Long uploaderId, String title, Pageable pageable);

    // 按状态查询
    Page<HaiGuiSoupAudit> findByUploaderIdAndAuditStatus(Long uploaderId, HaiGuiSoupAudit.AuditStatus status, Pageable pageable);

    // 组合查询（标题+状态）
    Page<HaiGuiSoupAudit> findByUploaderIdAndTitleContainingAndAuditStatus(
            Long uploaderId,
            String title,
            HaiGuiSoupAudit.AuditStatus status,
            Pageable pageable);

    // 查询所有海龟汤（分页）
    // Page<HaiGuiSoupAudit> findAll(Pageable pageable);

    // 按标题模糊查询所有海龟汤
    Page<HaiGuiSoupAudit> findByTitleContaining(String title, Pageable pageable);

    // 按状态查询所有海龟汤
    Page<HaiGuiSoupAudit> findByAuditStatus(HaiGuiSoupAudit.AuditStatus status, Pageable pageable);

    // 组合查询：标题+状态
    Page<HaiGuiSoupAudit> findByTitleContainingAndAuditStatus(
            String title,
            HaiGuiSoupAudit.AuditStatus status,
            Pageable pageable);
}
