package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.InferenceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 推理任务Repository接口
 */
@Repository
public interface InferenceTaskRepository extends JpaRepository<InferenceTask, Long> {

    /**
     * 根据海龟汤ID查找所有未删除的推理任务
     */
    List<InferenceTask> findBySoupIdAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID和推理层次查找未删除的推理任务
     */
    List<InferenceTask> findBySoupIdAndUnderstandingLevelAndIsDeletedFalse(String soupId, Integer understandingLevel);

    /**
     * 根据海龟汤ID查找所有必做推理任务
     */
    List<InferenceTask> findBySoupIdAndIsMandatoryTrueAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID查找所有可选推理任务
     */
    List<InferenceTask> findBySoupIdAndIsMandatoryFalseAndIsDeletedFalse(String soupId);

    /**
     * 根据任务ID和海龟汤ID查找推理任务
     */
    Optional<InferenceTask> findByTaskIdAndSoupIdAndIsDeletedFalse(Long taskId, String soupId);

    /**
     * 统计指定海龟汤的推理任务数量
     */
    @Query("SELECT COUNT(t) FROM InferenceTask t WHERE t.soupId = :soupId AND t.isDeleted = false")
    long countBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 统计指定海龟汤的必做推理任务数量
     */
    @Query("SELECT COUNT(t) FROM InferenceTask t WHERE t.soupId = :soupId AND t.isMandatory = true AND t.isDeleted = false")
    long countMandatoryTasksBySoupId(@Param("soupId") String soupId);

    /**
     * 软删除：根据海龟汤ID删除所有推理任务
     */
    @Modifying
    @Query("UPDATE InferenceTask t SET t.isDeleted = true WHERE t.soupId = :soupId AND t.isDeleted = false")
    int softDeleteBySoupId(@Param("soupId") String soupId);

    /**
     * 根据任务ID列表查找推理任务
     */
    List<InferenceTask> findByTaskIdInAndIsDeletedFalse(List<Long> taskIds);

    /**
     * 检查推理任务是否存在
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM InferenceTask t WHERE t.taskId = :taskId AND t.soupId = :soupId AND t.isDeleted = false")
    boolean existsByTaskIdAndSoupIdAndIsDeletedFalse(@Param("taskId") Long taskId, @Param("soupId") String soupId);

    /**
     * 根据海龟汤ID查找推理任务，按任务顺序排序
     */
    List<InferenceTask> findBySoupIdAndIsDeletedFalseOrderByTaskOrderAsc(String soupId);

    /**
     * 根据推理层次范围查找推理任务
     */
    @Query("SELECT t FROM InferenceTask t WHERE t.soupId = :soupId AND t.understandingLevel BETWEEN :minLevel AND :maxLevel AND t.isDeleted = false ORDER BY t.understandingLevel, t.taskOrder")
    List<InferenceTask> findBySoupIdAndUnderstandingLevelBetweenOrderByUnderstandingLevel(@Param("soupId") String soupId,
                                                                                          @Param("minLevel") Integer minLevel,
                                                                                          @Param("maxLevel") Integer maxLevel);

    /**
     * 计算指定海龟汤的总进度权重
     */
    @Query("SELECT COALESCE(SUM(t.progressWeight), 0.0) FROM InferenceTask t WHERE t.soupId = :soupId AND t.isDeleted = false")
    Double sumProgressWeightBySoupId(@Param("soupId") String soupId);

    /**
     * 计算指定海龟汤的必做任务总进度权重
     */
    @Query("SELECT COALESCE(SUM(t.progressWeight), 0.0) FROM InferenceTask t WHERE t.soupId = :soupId AND t.isMandatory = true AND t.isDeleted = false")
    Double sumMandatoryProgressWeightBySoupId(@Param("soupId") String soupId);
}