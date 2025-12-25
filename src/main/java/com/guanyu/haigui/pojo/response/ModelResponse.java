package com.guanyu.haigui.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelResponse {
    private Long id;
    private String endpointId;
    private String modelName;
    private String modelKey;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}