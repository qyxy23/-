package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserDetailsMapper {
    List<UserInfo> selectAllUsers();

    // @Select("SELECT * FROM sys_user WHERE username = #{username}")
    UserInfo selectUserInfoByUsername(String username);

    // @Select("SELECT * FROM sys_user WHERE id IN (#{userIds})")
    List<UserInfo> getUsersByIds(List<String> userIds);

    int insert(CustomUserDetails customUserDetails);
}