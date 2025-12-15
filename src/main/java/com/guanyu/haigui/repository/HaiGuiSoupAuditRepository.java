package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiSoupAuditRepository extends JpaRepository<HaiGuiSoupAudit, Long> {

}
