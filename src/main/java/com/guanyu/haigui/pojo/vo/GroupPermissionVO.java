package com.guanyu.haigui.pojo.vo;

import com.guanyu.haigui.pojo.model.GroupJoinRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupPermissionVO {
    private Long requestId;                  // 申请ID
    private String groupName;         // 申请的群名称
    private String description;       // 加群描述
    private String status;            // 申请状态（中文，如"待处理"）
    private LocalDateTime applyTime;  // 申请时间
    private LocalDateTime processTime;// 处理时间
    private String processorName;     // 处理人昵称（若未处理则为null）

    /**
     * 实体转VO：将GroupJoinRequest转换为GroupPermissionVO
     */
    public static GroupPermissionVO convertToGroupPermissionVO(GroupJoinRequest request) {
        return GroupPermissionVO.builder()
                .requestId(request.getId())
                .groupName(request.getGroup().getGroupName())   // 从申请中获取群名称
                .description(request.getDescription())         // 加群描述
                .status(request.getStatus().getDescription()) // 状态转中文
                .applyTime(request.getApplyTime())             // 申请时间
                .processTime(request.getProcessTime())         // 处理时间
                .processorName(                                // 处理人昵称（若未处理则为null）
                        request.getProcessor() != null ? request.getProcessor().getUsername() : null
                )
                .build();
    }
}