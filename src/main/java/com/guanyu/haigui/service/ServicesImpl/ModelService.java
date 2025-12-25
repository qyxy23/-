package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guanyu.haigui.pojo.dto.ModelCreateRequest;
import com.guanyu.haigui.pojo.model.AiInferenceEndpoint;
import com.guanyu.haigui.pojo.response.ModelResponse;
import com.guanyu.haigui.repository.AiInferenceEndpointRepository;
import com.guanyu.haigui.utils.RedisServiceUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {
    private final AiInferenceEndpointRepository modelRepository;
    private final RedisServiceUtil redisServiceUtil;

    public List<ModelResponse> getAllModels() {
        return modelRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ModelResponse createModel(ModelCreateRequest request) {
        // 检查唯一约束
        if (modelRepository.existsByEndpointId(request.getEndpointId())) {
            throw new IllegalArgumentException("接入点ID已存在: " + request.getEndpointId());
        }

        AiInferenceEndpoint entity = new AiInferenceEndpoint();
        entity.setEndpointId(request.getEndpointId());
        entity.setModelName(request.getModelName());
        entity.setModelKey(request.getModelKey());
        entity.setDescription(request.getDescription());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : false);

        AiInferenceEndpoint saved = modelRepository.save(entity);
        log.info("创建新模型配置: {}", saved.getModelName());
        return convertToResponse(saved);
    }

    public void deleteModel(Long id) {
        if (!modelRepository.existsById(id)) {
            throw new EntityNotFoundException("模型配置不存在, ID: " + id);
        }
        modelRepository.deleteById(id);
        log.info("删除模型配置, ID: {}", id);
    }

    public ModelResponse updateModelStatus(Long id, Boolean isActive) {
        AiInferenceEndpoint entity = modelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("模型配置不存在, ID: " + id));

        entity.setIsActive(isActive);
        AiInferenceEndpoint updated = modelRepository.save(entity);
        log.info("更新模型状态, ID: {}, 新状态: {}", id, isActive);
        return convertToResponse(updated);
    }

    private ModelResponse convertToResponse(AiInferenceEndpoint entity) {
        return new ModelResponse(
                entity.getId(),
                entity.getEndpointId(),
                entity.getModelName(),
                entity.getModelKey(),
                entity.getDescription(),
                entity.getIsActive(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }

    /**
     * 获取当前正在使用的大模型信息
     * 顺序：先查Redis，若为空则查数据库启用中的第一个
     */
    public ModelResponse getCurrentModel() {
        // 1. 尝试从Redis获取模型标识
        String modelEndpointId = redisServiceUtil.selectChatModel();

        if (StrUtil.isNotBlank(modelEndpointId)) {
            // 2. 如果Redis中有，尝试从数据库获取该模型信息
            Optional<AiInferenceEndpoint> modelOpt = modelRepository.findByEndpointId(modelEndpointId);
            if (modelOpt.isPresent() && modelOpt.get().getIsActive()) {
                log.info("从Redis获取到当前模型: {}", modelEndpointId);
                return convertToResponse(modelOpt.get());
            }
            log.warn("Redis中的模型配置无效或已禁用: {}", modelEndpointId);
        }

        // 3. 如果Redis中没有或无效，从数据库获取启用中的第一个模型
        log.info("从数据库获取当前模型");
        List<AiInferenceEndpoint> activeModels = modelRepository.findByIsActiveTrueOrderByIdAsc();

        if (CollUtil.isEmpty(activeModels)) {
            log.warn("数据库中没有启用的模型配置");
            return null;
        }

        AiInferenceEndpoint activeModel = activeModels.get(0);
        log.info("从数据库获取到当前模型: ID={}, Name={}, EndpointId={}",
                activeModel.getId(), activeModel.getModelName(), activeModel.getEndpointId());

        // 4. 将找到的模型缓存到Redis（可选）
        try {
            redisServiceUtil.updateChatModel(activeModel.getEndpointId());
            log.info("已将模型 {} 缓存到Redis", activeModel.getEndpointId());
        } catch (Exception e) {
            log.warn("模型缓存到Redis失败: {}", e.getMessage());
        }

        return convertToResponse(activeModel);
    }

    public ModelResponse updateCurModelStatus(Long id) {
        if (id ==  null) {
            throw new IllegalArgumentException("模型标识不能为空");
        }

        AiInferenceEndpoint model = modelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("模型配置不存在, EndpointId: " + id));

        redisServiceUtil.updateChatModel(model.getEndpointId());

        return convertToResponse(model);
    }
}