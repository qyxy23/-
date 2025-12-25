package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.AiInferenceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiInferenceEndpointRepository extends JpaRepository<AiInferenceEndpoint, Long> {

    // 按接入点ID查询（用于唯一性校验）
    boolean existsByEndpointId(String endpointId);

    // 按业务标识查询（用于唯一性校验）
    boolean existsByModelKey(String modelKey);

    // 按ID查询（带存在性检查）
    Optional<AiInferenceEndpoint> findByIdAndIsActiveTrue(Long id);

    // 查询所有启用的模型并按ID排序
    List<AiInferenceEndpoint> findByIsActiveTrueOrderByIdAsc();

    // 按接入点ID查询
    Optional<AiInferenceEndpoint> findByEndpointId(String endpointId);
}