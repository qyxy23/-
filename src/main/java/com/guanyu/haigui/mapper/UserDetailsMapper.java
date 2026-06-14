package com.guanyu.haigui.mapper;

import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.model.UserRole;
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

    @Select("SELECT * FROM sys_user WHERE wx_openid = #{openid}")
    UserInfo selectUserInfoByWxOpenid(String openid);

    @Select("SELECT * FROM sys_user WHERE wx_unionid = #{unionid}")
    UserInfo selectUserInfoByWxUnionid(String unionid);

    int updateWechatIdentity(UserInfo userInfo);

    @Select("SELECT * FROM sys_user WHERE id IN (#{userIds})")
    List<UserInfo> getUsersByIds(List<String> userIds);


    int insert(CustomUserDetails customUserDetails);

    int insertWechatUser(CustomUserDetails customUserDetails);


    int insertUserRole(UserRole userRole);


    String selectUserRolesByUsername(String username);

    List<String> selectUserRolesByUserId(Long userId);

    @Select("SELECT * FROM sys_user WHERE sys_user.user_id = #{creatorId}")
    UserInfo selectUserInfoById(Long creatorId);

    @Select("UPDATE sys_user SET password = #{password} WHERE sys_user.user_id = #{currentId}")
    void updateUserPassword(String password, Long currentId);

    @Select("UPDATE sys_user SET email = #{email} WHERE sys_user.user_id = #{currentId}")
    void updateUserEmail(String email, Long currentId);

    @Select("UPDATE sys_user SET phone = #{phone} WHERE sys_user.user_id = #{currentId}")
    void updateUserPhone(String phone, Long currentId);

    @Select("UPDATE sys_user SET username = #{username} WHERE sys_user.user_id = #{currentId}")
    void updateUserUsername(String username, Long currentId);
}