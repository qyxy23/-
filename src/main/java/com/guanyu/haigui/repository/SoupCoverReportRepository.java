package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.CoverReportStatus;
import com.guanyu.haigui.pojo.model.SoupCoverReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SoupCoverReportRepository extends JpaRepository<SoupCoverReport, Long> {

    boolean existsByReporterIdAndSoupIdAndCoverUrlAndStatus(
            Long reporterId, String soupId, String coverUrl, CoverReportStatus status);

    Page<SoupCoverReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    Page<SoupCoverReport> findByStatus(CoverReportStatus status, Pageable pageable);

    Page<SoupCoverReport> findByStatusNot(CoverReportStatus status, Pageable pageable);

    Page<SoupCoverReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SoupCoverReport> findBySoupIdAndCoverUrlAndStatus(
            String soupId, String coverUrl, CoverReportStatus status);

    boolean existsBySoupIdAndCoverUrlAndStatus(
            String soupId, String coverUrl, CoverReportStatus status);

    List<SoupCoverReport> findBySoupIdInAndStatus(
            Collection<String> soupIds, CoverReportStatus status);
}
