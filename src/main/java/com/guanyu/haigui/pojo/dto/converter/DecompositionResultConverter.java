package com.guanyu.haigui.pojo.dto.converter;

import com.guanyu.haigui.pojo.dto.ClueFragmentDTO;
import com.guanyu.haigui.pojo.dto.InferenceTaskDTO;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.result.DecompositionResult;
import com.guanyu.haigui.service.ServicesImpl.ClueDecompositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DecompositionResult converter
 * Responsible for converting DecompositionResult to DTO objects
 */
@Component
@Slf4j
public class DecompositionResultConverter {

    private final ClueDecompositionService clueDecompositionService;

    public DecompositionResultConverter(ClueDecompositionService clueDecompositionService) {
        this.clueDecompositionService = clueDecompositionService;
    }

    /**
     * 将DecompositionResult转换为TurtleSoupEnhancedContentDTO
     */
    public com.guanyu.haigui.pojo.dto.TurtleSoupEnhancedContentDTO convertToDTO(
            DecompositionResult decompositionResult, String soupTitle, String soupSurface, String soupBottom) {

        if (decompositionResult == null) {
            log.warn("DecompositionResult为空，返回空DTO");
            return new com.guanyu.haigui.pojo.dto.TurtleSoupEnhancedContentDTO();
        }

        try {
            // 转换线索片段
            List<ClueFragmentDTO> clueFragmentDTOs = decompositionResult.getFragments().stream()
                    .map(this::convertToClueFragmentDTO)
                    .collect(Collectors.toList());

            // 转换推理任务
            List<InferenceTaskDTO> inferenceTaskDTOs = decompositionResult.getInferenceTasks().stream()
                    .map(this::convertToInferenceTaskDTO)
                    .collect(Collectors.toList());

            log.info("Successfully converted DecompositionResult: fragments={}, tasks={}",
                clueFragmentDTOs.size(), inferenceTaskDTOs.size());

            com.guanyu.haigui.pojo.dto.TurtleSoupEnhancedContentDTO dto = new com.guanyu.haigui.pojo.dto.TurtleSoupEnhancedContentDTO();
            dto.setSoupTitle(soupTitle);
            dto.setSoupSurface(soupSurface);
            dto.setSoupBottom(soupBottom);
            dto.setClueFragments(clueFragmentDTOs);
            dto.setInferenceTasks(inferenceTaskDTOs);
            return dto;

        } catch (Exception e) {
            log.error("Failed to convert DecompositionResult", e);
            return new com.guanyu.haigui.pojo.dto.TurtleSoupEnhancedContentDTO();
        }
    }

    /**
     * 将ClueFragment实体转换为ClueFragmentDTO
     */
    private ClueFragmentDTO convertToClueFragmentDTO(ClueFragment fragment) {
        ClueFragmentDTO dto = new ClueFragmentDTO();
        dto.setFragmentId(fragment.getFragmentId());
        dto.setSoupId(fragment.getSoupId());
        dto.setFragmentContent(fragment.getFragmentContent());
        dto.setFragmentType(fragment.getFragmentType());
        dto.setInferenceLevel(fragment.getInferenceLevel());
        dto.setDifficulty(fragment.getDifficulty());
        dto.setImportance(fragment.getImportance());
        dto.setTriggerKeywords(fragment.getTriggerKeywords());
        dto.setIsCoreClue(fragment.getIsCoreClue());
        dto.setSimilarityThreshold(fragment.getSimilarityThreshold());
        dto.setAssociatedTaskIds(fragment.getAssociatedTaskIds());
        dto.setFragmentOrder(fragment.getFragmentOrder());
        dto.setGenerationSource(fragment.getGenerationSource());
        dto.setAiAnalysisConfidence(fragment.getAiAnalysisConfidence());
        dto.setVectorHash(fragment.getVectorHash());
        dto.setIsDeleted(fragment.getIsDeleted());

        return dto;
    }

    /**
     * Convert InferenceTask Map to InferenceTaskDTO
     */
    private InferenceTaskDTO convertToInferenceTaskDTO(Map<String, Object> task) {
        InferenceTaskDTO dto = new InferenceTaskDTO();
        dto.setTaskId((Long) task.get("taskId"));
        Object soupIdObj = task.get("soupId");
        if (soupIdObj != null) {
            dto.setSoupId(soupIdObj.toString());
        }
        dto.setTaskName((String) task.get("taskName"));
        dto.setDescription((String) task.get("description"));
        dto.setUnderstandingLevel((Integer) task.get("understandingLevel"));
        dto.setTargetKeywords((List<String>) task.get("targetKeywords"));
        dto.setReasoningGoal((String) task.get("reasoningGoal"));
        dto.setProgressWeight(((Number) task.get("progressWeight")).doubleValue());
        dto.setIsMandatory((Boolean) task.getOrDefault("isMandatory", false));
        dto.setTaskOrder((Integer) task.get("taskOrder"));
        // Note: No time fields in Map, set to null or current time
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setIsDeleted(false);

        return dto;
    }
}