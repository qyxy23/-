package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户信息仓库（Spring Data JPA 自动生成实现）
 */
@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    /**
     * 根据用户名查找用户（唯一，用于登录）
     */
    Optional<UserInfo> findByUsername(String username);

    /**
     * 根据手机号查找用户（唯一）
     */
    Optional<UserInfo> findByPhone(String phone);

    /**
     * 根据邮箱查找用户（唯一）
     */
    Optional<UserInfo> findByEmail(String email);

    /**
     * 根据用户名模糊查询（用于搜索用户）
     */
    List<UserInfo> findByUsernameContaining(String username);

    /**
     * 根据用户状态查找（如启用/禁用）
     */
    List<UserInfo> findByEnabled(boolean enabled);

    /**
     * 根据昵称模糊查询
     */
    List<UserInfo> findByNicknameContaining(String nickname);
}