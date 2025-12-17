package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.SoupTag;
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



        @Query(value = "SELECT new com.guanyu.haigui.pojo.dto.SoupProjectionDTO(" +
                "h.soupId, " +
                "h.soupTitle, " +
                "h.soupSurface, " +
                "h.soupBottom, " +
                "h.playCount, " +
                "h.uploaderId, " +
                "u.avatar, " +
                "h.uploadTime, " +
                "h.estimatedDuration, " +
                "h.playerCount, " +
                "h.difficultyLevel, " +
                "h.tags, " +
                "h.soupAvatar " +
                ") FROM HaiGuiSoup h " +
                "LEFT JOIN UserInfo u ON h.uploaderId = u.userId " +
                "WHERE h.isDeleted = false " +
                "AND h.isPublished = true " +
                "AND (:tagList IS NULL OR :tagList = '' OR h.tags = :tagList) " +
                "AND (:difficultyLevel IS NULL OR h.difficultyLevel = :difficultyLevel) " +
                "AND (:playerCount IS NULL OR h.playerCount = :playerCount OR :playerCount = 0) " +
                "AND (:minDuration IS NULL OR h.estimatedDuration >= :minDuration) " +
                "AND (:maxDuration IS NULL OR h.estimatedDuration <= :maxDuration) " +
                "ORDER BY h.uploadTime DESC")
        Page<SoupProjectionDTO> findSoupsWithPagination(
                Pageable pageable,
                @Param("tagList") SoupTag tagList,
                @Param("difficultyLevel") DifficultyLevel difficultyLevel, // 使用正确的枚举类型
                @Param("playerCount") Integer playerCount,
                @Param("minDuration") Integer minDuration,
                @Param("maxDuration") Integer maxDuration);

}
