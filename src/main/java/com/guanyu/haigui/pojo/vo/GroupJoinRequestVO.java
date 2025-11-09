package com.guanyu.haigui.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupJoinRequestVO {
    private Long requestId;                  // 申请ID
    private String applicantName;     // 申请人昵称
    private String groupName;         // 群名称
    private String description;       // 加群描述
    private String status;            // 申请状态（中文，如"待处理"）
    private LocalDateTime applyTime;  // 申请时间
    private LocalDateTime processTime;// 处理时间
    private String processorName;     // 处理人昵称
}