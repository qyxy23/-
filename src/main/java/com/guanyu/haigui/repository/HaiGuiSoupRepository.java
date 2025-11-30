package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.dto.SoupProjectionDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiSoupRepository extends JpaRepository<HaiGuiSoup, String> {

    /**
     * 分页查询海龟汤列表，返回指定字段
     * @param pageable 分页参数
     * @return 分页后的海龟汤列表
     */
    @Query("SELECT new com.guanyu.haigui.pojo.dto.SoupProjectionDTO(" +
           "h.soupId, h.soupTitle, h.soupSurface, h.soupBottom, " +
           "h.playCount, h.uploaderId, u.avatar, h.uploadTime) " +
           "FROM HaiGuiSoup h " +
           "LEFT JOIN UserInfo u ON h.uploaderId = u.userId " +
           "WHERE h.isDeleted = false " +
           "ORDER BY h.uploadTime DESC")
    Page<SoupProjectionDTO> findSoupsWithPagination(Pageable pageable);
}
