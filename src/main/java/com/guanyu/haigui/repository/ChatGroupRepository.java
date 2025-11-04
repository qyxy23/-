package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, String> {
    /**
     * 根据用户ID查询已加入的群聊（通过群成员关联）
     * @param userId 当前用户ID
     * @return 已加入的群聊列表
     */
    @Query("SELECT DISTINCT cg FROM ChatGroup cg " +
            "INNER JOIN cg.members cgm " + // 关联群成员表
            "WHERE cgm.member.userId = :userId") // 过滤当前用户
    List<ChatGroup> findJoinedGroupsByUserId(@Param("userId") Long userId);
}
