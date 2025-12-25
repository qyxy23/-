package com.guanyu.haigui.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelCreateRequest {
    @NotBlank(message = "接入点ID不能为空")
    @Size(max = 100, message = "接入点ID长度不能超过100字符")
    private String endpointId;
    
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 50, message = "模型名称长度不能超过50字符")
    private String modelName;
    
    @NotBlank(message = "业务标识不能为空")
    @Size(max = 50, message = "业务标识长度不能超过50字符")
    private String modelKey;
    
    @Size(max = 255, message = "描述长度不能超过255字符")
    private String description;
    
    private Boolean isActive = false;
}