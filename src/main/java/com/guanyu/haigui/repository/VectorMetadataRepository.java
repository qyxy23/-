package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.VectorType;
import com.guanyu.haigui.pojo.model.VectorMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 向量元数据数据访问接口
 */
@Repository
public interface VectorMetadataRepository extends JpaRepository<VectorMetadata, String> {

    /**
     * 根据海龟汤ID查找所有向量元数据
     * @param soupId 海龟汤ID
     * @return 向量元数据列表
     */
    List<VectorMetadata> findBySoupIdAndIsDeletedFalse(String soupId);

    /**
     * 根据海龟汤ID和向量类型查找向量元数据
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @return 向量元数据
     */
    Optional<VectorMetadata> findBySoupIdAndVectorTypeAndIsDeletedFalse(String soupId, VectorType vectorType);

    /**
     * 根据Redis键名查找向量元数据
     * @param redisKey Redis键名
     * @return 向量元数据
     */
    Optional<VectorMetadata> findByRedisKeyAndIsDeletedFalse(String redisKey);

    /**
     * 根据向量类型查找向量元数据
     * @param vectorType 向量类型
     * @return 向量元数据列表
     */
    List<VectorMetadata> findByVectorTypeAndIsDeletedFalse(VectorType vectorType);

    /**
     * 根据海龟汤ID和向量类型列表查找向量元数据
     * @param soupId 海龟汤ID
     * @param vectorTypes 向量类型列表
     * @return 向量元数据列表
     */
    List<VectorMetadata> findBySoupIdAndVectorTypeInAndIsDeletedFalse(String soupId, List<VectorType> vectorTypes);

    /**
     * 统计海龟汤的向量元数据数量
     * @param soupId 海龟汤ID
     * @return 向量元数据数量
     */
    @Query("SELECT COUNT(v) FROM VectorMetadata v WHERE v.soupId = :soupId AND v.isDeleted = false")
    Long countBySoupIdAndIsDeletedFalse(@Param("soupId") String soupId);

    /**
     * 检查指定类型的向量是否已存在
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @return 是否存在
     */
    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM VectorMetadata v WHERE v.soupId = :soupId AND v.vectorType = :vectorType AND v.isDeleted = false")
    boolean existsBySoupIdAndVectorTypeAndIsDeletedFalse(@Param("soupId") String soupId, @Param("vectorType") VectorType vectorType);

    /**
     * 批量删除海龟汤的所有向量元数据（逻辑删除）
     * @param soupId 海龟汤ID
     * @return 删除的行数
     */
    @Query("UPDATE VectorMetadata v SET v.isDeleted = true WHERE v.soupId = :soupId AND v.isDeleted = false")
    int deleteBySoupId(@Param("soupId") String soupId);

    /**
     * 根据Redis键名列表查找向量元数据
     * @param redisKeys Redis键名列表
     * @return 向量元数据列表
     */
    List<VectorMetadata> findByRedisKeyInAndIsDeletedFalse(List<String> redisKeys);

    /**
     * 根据向量类型和海龟汤ID列表查找向量元数据
     * @param vectorType 向量类型
     * @param soupIds 海龟汤ID列表
     * @return 向量元数据列表
     */
    List<VectorMetadata> findByVectorTypeAndSoupIdInAndIsDeletedFalse(VectorType vectorType, List<String> soupIds);

    /**
     * 查找所有线索类型的向量元数据
     * @return 线索向量元数据列表
     */
    List<VectorMetadata> findByVectorTypeEqualsAndIsDeletedFalse(VectorType vectorType);
}