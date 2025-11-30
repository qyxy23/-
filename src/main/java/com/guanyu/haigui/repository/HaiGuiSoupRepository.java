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
     * @param tags 标签筛选
     * @param difficultyLevel 难度筛选
     * @param playerCount 人数筛选
     * @param minDuration 最小时长筛选
     * @param maxDuration 最大时长筛选
     * @return 分页后的海龟汤列表
     */
    @Query("SELECT new com.guanyu.haigui.pojo.dto.SoupProjectionDTO(" +
           "h.soupId, h.soupTitle, h.soupSurface, h.soupBottom, " +
           "h.playCount, h.uploaderId, u.avatar, h.uploadTime, " +
           "h.estimatedDuration, h.playerCount, h.difficultyLevel, h.tags) " +
           "FROM HaiGuiSoup h " +
           "LEFT JOIN UserInfo u ON h.uploaderId = u.userId " +
           "WHERE h.isDeleted = false " +
           "AND (:tags IS NULL OR h.tags LIKE CONCAT('%', :tags, '%') OR h.tags LIKE CONCAT('%\"', :tags, '\"%')) " +
           "AND (:difficultyLevel IS NULL OR h.difficultyLevel = :difficultyLevel) " +
           "AND (:playerCount IS NULL OR h.playerCount = :playerCount OR :playerCount = 0) " +
           "AND (:minDuration IS NULL OR h.estimatedDuration >= :minDuration) " +
           "AND (:maxDuration IS NULL OR h.estimatedDuration <= :maxDuration) " +
           "ORDER BY h.uploadTime DESC")
    Page<SoupProjectionDTO> findSoupsWithPagination(Pageable pageable,
                                                   @Param("tags") String tags,
                                                   @Param("difficultyLevel") String difficultyLevel,
                                                   @Param("playerCount") Integer playerCount,
                                                   @Param("minDuration") Integer minDuration,
                                                   @Param("maxDuration") Integer maxDuration);
}
