package com.guanyu.haigui.pojo.dto;

import lombok.Data;
@Data
public class CharGroupListDTO {
    private String groupName;   // 群名关键词（模糊查询）
    private String sortField="memberCount";   // 排序字段：memberCount（人数）/ createTime（创建时间）
    private String sortOrder="desc";   // 排序方向：asc（升序）/ desc（降序）
    private int pageSize = 10;  // 每页大小（默认10条）
}
