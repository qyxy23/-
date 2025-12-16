package com.guanyu.haigui.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class QueryMyTurtleSoupListVO {
    private Integer total; // 总记录数
    private Integer pages; // 总页数
    private List<QueryMyTurtleSoupListVO.TurtleSoupItem> list; // 当前页数据

    @Data
    public static class TurtleSoupItem {
        private Long auditId;// 审核ID
        private String soupTitle; // 标题
        private String soupSurface; // 汤面
        private String soupBottom;//汤底
        private String soupTags; // 汤的标签
        private Integer soupEstimatedQuestions; // 汤的题目数量
        private String soupDifficultyLevel; // 汤的难易程度
        private String soupPlayerCount; // 汤的玩家数量
        private String soupEstimatedDuration; // 汤的时长
        private String auditStatus; // 审核状态
        private String rejectReason;//拒绝原因
        private String createTime; // 创建时间(上传时间)
    }
}
