package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.ClueFragment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 线索片段Repository接口
 */
@Repository
public interface ClueFragmentRepository extends JpaRepository<ClueFragment, Long> {

    /**
     * 根据海龟汤ID查找所有未删除的线索片段
     */
    List<ClueFragment> findBySoupIdAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID和片段类型查找未删除的线索片段
     */
    List<ClueFragment> findBySoupIdAndFragmentTypeAndIsDeletedFalse(String soupId, String fragmentType);

    /**
     * 根据海龟汤ID和推理层次查找未删除的线索片段
     */
    List<ClueFragment> findBySoupIdAndInferenceLevelAndIsDeletedFalse(String soupId, Integer inferenceLevel);

    /**
     * 根据海龟汤ID查找所有核心线索片段
     */
    List<ClueFragment> findBySoupIdAndIsCoreClueTrueAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID查找所有非核心线索片段
     */
    List<ClueFragment> findBySoupIdAndIsCoreClueFalseAndIsDeletedFalse(String soupId);

    /**
     * 根据片段ID和海龟汤ID查找线索片段
     */
    Optional<ClueFragment> findByFragmentIdAndSoupIdAndIsDeletedFalse(Long fragmentId, String soupId);

    /**
     * 统计指定海龟汤的线索片段数量
     */
    @Query("SELECT COUNT(f) FROM ClueFragment f WHERE f.soupId = :soupId AND f.isDeleted = false")
    long countBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 统计指定海龟汤的核心线索片段数量
     */
    @Query("SELECT COUNT(f) FROM ClueFragment f WHERE f.soupId = :soupId AND f.isCoreClue = true AND f.isDeleted = false")
    long countKeyFragmentsBySoupId(@Param("soupId") String soupId);

    /**
     * 软删除：根据海龟汤ID删除所有线索片段
     */
    @Modifying
    @Query("UPDATE ClueFragment f SET f.isDeleted = true WHERE f.soupId = :soupId AND f.isDeleted = false")
    int softDeleteBySoupId(@Param("soupId") String soupId);

    /**
     * 根据片段ID列表查找线索片段
     */
    List<ClueFragment> findByFragmentIdInAndIsDeletedFalse(List<Long> fragmentIds);

    /**
     * 检查线索片段是否存在
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM ClueFragment f WHERE f.fragmentId = :fragmentId AND f.soupId = :soupId AND f.isDeleted = false")
    boolean existsByFragmentIdAndSoupIdAndIsDeletedFalse(@Param("fragmentId") Long fragmentId, @Param("soupId") String soupId);

    /**
     * 根据推理层次范围查找线索片段
     */
    @Query("SELECT f FROM ClueFragment f WHERE f.soupId = :soupId AND f.inferenceLevel BETWEEN :minLevel AND :maxLevel AND f.isDeleted = false ORDER BY f.inferenceLevel, f.fragmentOrder")
    List<ClueFragment> findBySoupIdAndInferenceLevelBetweenOrderByInferenceLevel(@Param("soupId") String soupId,
                                                                               @Param("minLevel") Integer minLevel,
                                                                               @Param("maxLevel") Integer maxLevel);

    /**
     * 根据生成来源查找线索片段
     */
    List<ClueFragment> findBySoupIdAndGenerationSourceAndIsDeletedFalse(String soupId, String generationSource);

    /**
     * 获取所有不重复的海龟汤ID
     */
    @Query("SELECT DISTINCT f.soupId FROM ClueFragment f WHERE f.isDeleted = false")
    List<String> findAllDistinctSoupIds();
}