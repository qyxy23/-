package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.ClueType;
import com.guanyu.haigui.pojo.model.SoupClue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 海龟汤线索数据访问接口
 */
@Repository
public interface SoupClueRepository extends JpaRepository<SoupClue, String> {

    /**
     * 根据海龟汤ID查找所有线索
     * @param soupId 海龟汤ID
     * @return 线索列表
     */
    List<SoupClue> findBySoupIdAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID和线索类型查找线索
     * @param soupId 海龟汤ID
     * @param clueType 线索类型
     * @return 线索列表
     */
    List<SoupClue> findBySoupIdAndClueTypeAndIsDeletedFalse(String soupId, ClueType clueType);

    /**
     * 根据海龟汤ID查找关键线索
     * @param soupId 海龟汤ID
     * @return 关键线索列表
     */
    List<SoupClue> findBySoupIdAndIsKeyTrueAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID查找非关键线索
     * @param soupId 海龟汤ID
     * @return 非关键线索列表
     */
    List<SoupClue> findBySoupIdAndIsKeyFalseAndIsDeletedFalse(String soupId);

    /**
     * 根据线索ID和海龟汤ID查找线索
     * @param clueId 线索ID
     * @param soupId 海龟汤ID
     * @return 线索
     */
    Optional<SoupClue> findByClueIdAndSoupIdAndIsDeletedFalse(String clueId, String soupId);

    /**
     * 统计海龟汤的线索总数
     * @param soupId 海龟汤ID
     * @return 线索总数
     */
    @Query("SELECT COUNT(c) FROM SoupClue c WHERE c.soupId = :soupId AND c.isDeleted = false")
    Long countBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 统计海龟汤的关键线索数
     * @param soupId 海龟汤ID
     * @return 关键线索数
     */
    @Query("SELECT COUNT(c) FROM SoupClue c WHERE c.soupId = :soupId AND c.isKey = true AND c.isDeleted = false")
    Long countKeyCluesBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 批量删除海龟汤的所有线索（逻辑删除）
     * @param soupId 海龟汤ID
     * @return 删除的行数
     */
    @Query("UPDATE SoupClue c SET c.isDeleted = true WHERE c.soupId = :soupId AND c.isDeleted = false")
    int deleteBySoupId(@Param("soupId") String soupId);

    /**
     * 根据线索ID列表查找线索
     * @param clueIds 线索ID列表
     * @return 线索列表
     */
    List<SoupClue> findByClueIdInAndIsDeletedFalse(List<String> clueIds);

    /**
     * 检查线索是否存在且属于指定海龟汤
     * @param clueId 线索ID
     * @param soupId 海龟汤ID
     * @return 是否存在
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM SoupClue c WHERE c.clueId = :clueId AND c.soupId = :soupId AND c.isDeleted = false")
    boolean existsByClueIdAndSoupIdAndIsDeletedFalse(@Param("clueId") String clueId, @Param("soupId") String soupId);
}