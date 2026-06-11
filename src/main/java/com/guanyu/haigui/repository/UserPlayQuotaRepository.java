package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.UserPlayQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPlayQuotaRepository extends JpaRepository<UserPlayQuota, Long> {
}
