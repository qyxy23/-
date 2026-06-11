package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {
}
