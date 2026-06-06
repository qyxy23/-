package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE HaiGuiSoupAudit a
            SET a.aiGenStatus = com.guanyu.haigui.Enum.AiGenStatus.GENERATING,
                a.aiGenError = null,
                a.aiGenUpdatedAt = :time
            WHERE a.auditId = :auditId AND a.aiGenStatus <> com.guanyu.haigui.Enum.AiGenStatus.GENERATING
            """)
    int markGeneratingIfNot(@Param("auditId") Long auditId, @Param("time") LocalDateTime time);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE HaiGuiSoupAudit a
            SET a.publishStatus = com.guanyu.haigui.Enum.PublishStatus.PUBLISHING,
                a.publishError = null,
                a.publishUpdatedAt = :time
            WHERE a.auditId = :auditId
              AND a.auditStatus = com.guanyu.haigui.pojo.model.HaiGuiSoupAudit.AuditStatus.PENDING
              AND a.publishStatus <> com.guanyu.haigui.Enum.PublishStatus.PUBLISHING
            """)
    int markPublishingIfNot(@Param("auditId") Long auditId, @Param("time") LocalDateTime time);
}
