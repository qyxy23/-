package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.dto.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface UserRoleMapper {
    // 插入用户角色关系
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(UserRole userRole);
}