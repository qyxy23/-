package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.PlayQuotaLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayQuotaLedgerRepository extends JpaRepository<PlayQuotaLedger, Long> {
}
