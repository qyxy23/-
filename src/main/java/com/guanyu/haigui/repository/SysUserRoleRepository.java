package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, SysUserRole.UserRoleId> {

}
