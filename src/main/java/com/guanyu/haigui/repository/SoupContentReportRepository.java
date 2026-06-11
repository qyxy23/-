package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.CoverReportStatus;
import com.guanyu.haigui.Enum.SoupContentReportStatus;
import com.guanyu.haigui.pojo.model.SoupContentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SoupContentReportRepository extends JpaRepository<SoupContentReport, Long> {

    boolean existsByReporterIdAndSoupIdAndStatus(
            Long reporterId, String soupId, SoupContentReportStatus status);

    Page<SoupContentReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    Page<SoupContentReport> findByStatus(SoupContentReportStatus status, Pageable pageable);

    Page<SoupContentReport> findByStatusNot(SoupContentReportStatus status, Pageable pageable);

    Page<SoupContentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SoupContentReport> findBySoupIdAndStatus(String soupId, SoupContentReportStatus status);
}
