# 海龟汤AI增强结果 - 数据库映射指南

## 概述

海龟汤AI增强接口返回的数据可以直接映射到数据库表中，但需要进行适当的转换。以下是详细的映射指南。

## 数据库表结构

### 1. hai_gui_soup 表（主表）

| 字段名 | 类型 | AI接口对应字段 | 说明 |
|--------|------|---------------|------|
| soup_id | VARCHAR(36) | 新生成UUID | 系统生成 |
| soup_title | VARCHAR(255) | `enhancedTitle` 或 `soupTitle` | AI优化的标题或原标题 |
| soup_surface | TEXT | `soupSurface` | 用户输入的汤面 |
| soup_bottom | TEXT | `soupBottom` | 用户输入的汤底 |
| host_manual | TEXT | `hostManual` | AI生成的主持人手册 |
| key_clues | JSON | `keyClues` (需要转换) | 线索ID列表，不是线索内容 |
| creator_id | BIGINT | 当前用户ID | 从BaseContext获取 |
| uploader_id | BIGINT | 当前用户ID | 从BaseContext获取 |
| play_count | INT | 0 | 初始值 |

### 2. hai_gui_soup_clue 表（线索表）

| 字段名 | 类型 | AI接口对应字段 | 说明 |
|--------|------|---------------|------|
| clue_id | VARCHAR(36) | 新生成UUID | 系统生成 |
| soup_id | VARCHAR(36) | 新生成UUID | 关联主表 |
| clue_content | TEXT | `keyClues[*].content` | 线索内容 |
| clue_type | ENUM | `keyClues[*].clueType` | 转换枚举值 |
| is_key | TINYINT(1) | `keyClues[*].isKey` | 布尔值转换 |

### 3. hai_gui_soup_progress_task 表（进度任务表）

| 字段名 | 类型 | AI接口对应字段 | 说明 |
|--------|------|---------------|------|
| task_id | VARCHAR(50) | 新生成ID | 格式：TASK_XXX |
| soup_id | VARCHAR(36) | 新生成UUID | 关联主表 |
| name | VARCHAR(255) | `progressSettings[*].taskName` | 任务名称 |
| description | TEXT | `progressSettings[*].description` | 任务描述 |
| condition_type | ENUM | "CLUE_TRIGGER" | 默认值 |
| condition_value | JSON | 线索ID列表 | 关联线索表 |
| increment | DECIMAL(5,2) | `progressSettings[*].increment` | 进度增量 |

## 数据转换代码示例

### Java转换方法

```java
public class SoupDataConverter {

    /**
     * 转换AI增强结果到数据库实体
     */
    public void saveEnhancedSoup(TurtleSoupEnhanceResultVO result,
                                String soupTitle, String soupSurface, String soupBottom) {

        // 1. 创建主表记录
        HaiGuiSoup soup = new HaiGuiSoup();
        soup.setSoupId(UUID.randomUUID().toString());
        soup.setSoupTitle(result.getEnhancedTitle() != null ?
                         result.getEnhancedTitle() : soupTitle);
        soup.setSoupSurface(soupSurface);
        soup.setSoupBottom(soupBottom);
        soup.setHostManual(result.getHostManual());
        soup.setCreatorId(BaseContext.getCurrentId());
        soup.setUploaderId(BaseContext.getCurrentId());
        soup.setPlayCount(0);

        // 2. 保存线索表记录
        List<SoupClue> clues = convertClues(result.getKeyClues(), soup.getSoupId());
        List<String> clueIds = clues.stream()
                                   .map(SoupClue::getClueId)
                                   .collect(Collectors.toList());

        // 3. 设置线索ID列表
        soup.setKeyClues(objectMapper.writeValueAsString(clueIds));

        // 4. 保存进度任务
        List<HaiGuiSoupProgressTask> tasks = convertTasks(
            result.getProgressTasks(), soup.getSoupId(), clueIds);

        // 5. 保存到数据库
        haiGuiSoupRepository.save(soup);
        soupClueRepository.saveAll(clues);
        progressTaskRepository.saveAll(tasks);
    }

    /**
     * 转换线索数据
     */
    private List<SoupClue> convertClues(String keyCluesJson, String soupId) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cluesData = objectMapper.readValue(
                keyCluesJson, List.class);

            return cluesData.stream().map(clueData -> {
                SoupClue clue = new SoupClue();
                clue.setClueId(UUID.randomUUID().toString());
                clue.setSoupId(soupId);
                clue.setClueContent((String) clueData.get("content"));
                clue.setClueType(convertClueType((String) clueData.get("clueType")));
                clue.setIsKey((Boolean) clueData.get("isKey"));
                return clue;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("转换线索数据失败", e);
            throw new BusinessException("数据格式错误");
        }
    }

    /**
     * 转换线索类型枚举
     */
    private ClueType convertClueType(String aiClueType) {
        switch (aiClueType.toLowerCase()) {
            case "time": return ClueType.TIME;
            case "place": return ClueType.PLACE;
            case "character": return ClueType.CHARACTER;
            case "plot": return ClueType.PLOT;
            default: return ClueType.PLOT;
        }
    }
}
```

## 字段映射规则

### 1. 线索类型映射

| AI输出值 | 数据库枚举值 |
|---------|------------|
| "main" | "PLOT" |
| "side" | "CHARACTER" 或 "TIME" 或 "PLACE" |
| "red_herring" | "TIME" 或 "PLACE" |
| "TIME" | "TIME" |
| "PLACE" | "PLACE" |
| "CHARACTER" | "CHARACTER" |
| "PLOT" | "PLOT" |

### 2. 进度增量规则

- AI输出：`increment: 15.0` 表示15%的进度增量
- 所有任务的increment总和必须等于100.0
- 简单任务：10-20%
- 中等任务：20-40%
- 困难任务：40-60%

### 3. 线索ID生成规则

- 格式：UUID字符串
- 示例：`"f47ac10b-58cc-4372-a567-0e02b2c3d479"`

### 4. 任务ID生成规则

- 格式：`TASK_` + 业务关键词 + 序号
- 示例：`TASK_INVESTIGATION_1`, `TASK_MYSTERY_2`

## 创建海龟汤流程

### 1. 接收AI增强结果
```java
TurtleSoupEnhanceResultVO result = haiGuiTangService.enhanceTurtleSoup(enhanceDTO);
```

### 2. 转换并保存数据
```java
soupDataConverter.saveEnhancedSoup(
    result,
    enhanceDTO.getSoupTitle(),
    enhanceDTO.getSoupSurface(),
    enhanceDTO.getSoupBottom()
);
```

### 3. 向量化处理（可选）
```java
// 调用现有的向量化功能
boolean vectorSuccess = vectorService.vectorizeAndStoreSoupContext(soup, clues);
```

## 注意事项

1. **事务处理**：确保主表、线索表、任务表的数据一致性
2. **ID关联**：线索表和任务表必须正确关联到主表
3. **枚举转换**：AI输出的线索类型需要转换为数据库枚举值
4. **JSON格式**：key_clues字段必须存储线索ID的JSON数组，不是线索内容
5. **进度验证**：确保所有任务的increment总和等于100.0

## 示例数据

### AI增强结果
```json
{
  "progressSettings": [
    {"taskName": "基础调查", "difficulty": "easy", "increment": 15.0},
    {"taskName": "称呼之谜", "difficulty": "medium", "increment": 30.0},
    {"taskName": "真相还原", "difficulty": "hard", "increment": 55.0}
  ],
  "keyClues": [
    {"content": "星际旅行和虫洞", "isKey": true, "clueType": "TIME"},
    {"content": "地球上没有人类痕迹", "isKey": true, "clueType": "PLACE"}
  ]
}
```

### 转换后的数据库记录

**hai_gui_soup 表**:
```
soup_id: "abc-123"
soup_title: "墓碑上的文字"
key_clues: '["clue-456", "clue-789"]'  # 只存储ID，不存储内容
```

**hai_gui_soup_clue 表**:
```
clue_id: "clue-456"
clue_content: "星际旅行和虫洞"
clue_type: "TIME"
is_key: 1
```

**hai_gui_soup_progress_task 表**:
```
task_id: "TASK_INVESTIGATION_1"
increment: 15.0
```

通过这样的映射关系，AI增强的结果可以直接用于创建完整的海龟汤数据记录。