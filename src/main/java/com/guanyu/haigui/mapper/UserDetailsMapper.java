package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.dto.UserRole;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserDetailsMapper {
    @Select("SELECT * FROM sys_user")
    List<UserInfo> selectAllUsers();

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    UserInfo selectUserInfoByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE id IN (#{userIds})")
    List<UserInfo> getUsersByIds(List<String> userIds);


    int insert(CustomUserDetails customUserDetails);


    int insertUserRole(UserRole userRole);


    String selectUserRolesByUsername(String username);

    List<String> selectUserRolesByUserId(Long userId);

}