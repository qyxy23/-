# 海龟汤AI增强API文档

## 接口概述

海龟汤AI增强接口可以根据用户输入的汤面、汤底等信息，智能生成完善的进度任务列表、关键线索和主持人手册。支持8种不同的输入组合，自动选择最适合的处理方式。

## 接口地址

### 1. 海龟汤AI增强接口
```
POST /enhanceTurtleSoup
```

### 2. 海龟汤标题生成接口（新增）
```
POST /generateTitle
```

## 请求参数

### TurtleSoupEnhanceDTO

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| soupTitle | String | 否 | 海龟汤标题 |
| soupSurface | String | 是 | 汤面（故事背景） |
| soupBottom | String | 是 | 汤底（真相解答） |
| progressTasks | String | 否 | 用户提前写好的进度设置列表（JSON格式） |
| keyClues | String | 否 | 用户提前写好的关键线索（JSON格式） |
| hostManual | String | 否 | 用户提前写好的主持人手册 |

### TitleGenerateDTO

| 字段名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| soupSurface | String | 是 | 汤面（故事背景） |
| soupBottom | String | 是 | 汤底（真相解答） |
| currentTitle | String | 否 | 当前标题（可选，用于对比） |

## 响应参数

### TurtleSoupEnhanceResultVO

| 字段名 | 类型 | 描述 |
|--------|------|------|
| progressSettings | String | 进度设置列表（JSON格式） |
| keyClues | String | 关键线索列表（JSON格式） |
| hostManual | String | 主持人手册内容 |
| status | String | 生成状态信息 |
| promptType | String | 使用的prompt类型（用于调试） |

### TitleGenerateResultVO

| 字段名 | 类型 | 描述 |
|--------|------|------|
| generatedTitle | String | AI生成的标题 |
| status | String | 生成状态 |
| titleType | String | 标题类型（original/optimized） |
| suggestion | String | 生成说明或建议 |

## 8种处理情况

### 情况1：只有汤面和汤底 (TYPE_1_SURFACE_BOTTOM_ONLY)
- **输入**：soupSurface, soupBottom
- **输出**：progressSettings, keyClues, hostManual
- **适用场景**：用户只提供基本故事，需要AI生成完整游戏内容

### 情况2：已有进度设置 (TYPE_2_WITH_PROGRESS_SETTINGS)
- **输入**：soupSurface, soupBottom, progressSettings
- **输出**：keyClues, hostManual
- **适用场景**：用户已设计好进度设置，需要AI生成对应的线索和手册

### 情况3：已有关键线索 (TYPE_3_WITH_KEY_CLUES)
- **输入**：soupSurface, soupBottom, keyClues
- **输出**：progressSettings, hostManual
- **适用场景**：用户已设计好关键线索，需要AI生成对应的进度设置和手册

### 情况4：已有主持人手册 (TYPE_4_WITH_HOST_MANUAL)
- **输入**：soupSurface, soupBottom, hostManual
- **输出**：progressSettings, keyClues
- **适用场景**：用户已写好主持人手册，需要AI生成对应的进度设置和线索

### 情况5：已有进度设置和关键线索 (TYPE_5_WITH_PROGRESS_AND_CLUES)
- **输入**：soupSurface, soupBottom, progressSettings, keyClues
- **输出**：hostManual
- **适用场景**：用户已设计好进度设置和线索，只需要AI生成主持人手册

### 情况6：已有进度设置和主持人手册 (TYPE_6_WITH_PROGRESS_AND_MANUAL)
- **输入**：soupSurface, soupBottom, progressSettings, hostManual
- **输出**：keyClues
- **适用场景**：用户已设计好进度设置和手册，只需要AI生成关键线索

### 情况7：已有关键线索和主持人手册 (TYPE_7_WITH_CLUES_AND_MANUAL)
- **输入**：soupSurface, soupBottom, keyClues, hostManual
- **输出**：progressSettings
- **适用场景**：用户已设计好关键线索和手册，只需要AI生成进度设置

### 情况8：信息完整优化 (TYPE_8_COMPLETE_OPTIMIZATION)
- **输入**：全部字段
- **输出**：优化建议和改进内容
- **适用场景**：用户已提供所有信息，需要AI提供优化建议

## 使用示例

### 示例1：只有基本故事

```json
{
  "soupTitle": "双鱼玉佩",
  "soupSurface": "一位探险家在新疆沙漠中发现了双鱼玉佩，这块玉佩似乎有复制生物的能力...",
  "soupBottom": "双鱼玉佩是一个外星文明的生物复制装置，探险家被复制后出现了两个相同的自己..."
}
```

### 示例2：已有进度任务

```json
{
  "soupTitle": "教室幽灵",
  "soupSurface": "深夜的教室里总会传来奇怪的哭声，有人说看到一个穿着校服的幽灵...",
  "soupBottom": "其实是一个清洁工的女儿，她因为思念去世的母亲而经常晚上来教室看母亲留下的物品...",
  "progressTasks": "[{\"taskName\":\"调查哭声来源\",\"description\":\"找出哭声的具体位置和时间\",\"points\":10,\"difficulty\":\"easy\"}]"
}
```

### 示例3：完整信息优化

```json
{
  "soupTitle": "神秘邻居",
  "soupSurface": "新邻居从不出门，但每天晚上都能听到他家传来音乐声...",
  "soupBottom": "新邻居是一位盲人音乐家，因为自卑而很少出门，晚上是他的创作时间...",
  "progressTasks": "[{\"taskName\":\"观察邻居作息\",\"description\":\"记录邻居的活动规律\",\"points\":5,\"difficulty\":\"easy\"}]",
  "keyClues": "[{\"content\":\"门口有盲道标识\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":1}]",
  "hostManual": "这是一个关于理解和包容的故事..."
}
```

## 返回示例

### 成功响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "enhancedTitle": "双鱼玉佩之谜",
    "progressTasks": "[{\"taskName\":\"调查玉佩来源\",\"description\":\"研究双鱼玉佩的历史背景和发现过程\",\"points\":15,\"difficulty\":\"medium\"}]",
    "keyClues": "[{\"content\":\"探险家发现周围出现了和自己一模一样的人\",\"isKey\":true,\"clueType\":\"main\",\"difficulty\":3}]",
    "hostManual": "游戏主持人应引导玩家逐步发现玉佩的复制功能...",
    "status": "success",
    "promptType": "TYPE_1_SURFACE_BOTTOM_ONLY"
  }
}
```

## 注意事项

1. **必填字段**：soupSurface 和 soupBottom 为必填字段
2. **JSON格式**：progressTasks 和 keyClues 需要符合指定的JSON格式
3. **AI依赖**：接口依赖于AI服务的可用性，请确保AI服务正常工作
4. **响应时间**：AI处理可能需要几秒钟，请做好异步处理
5. **错误处理**：建议在客户端做好错误处理和重试机制
6. **JSON解析**：系统已集成智能JSON清理功能，能够处理AI返回的不规范JSON格式，包括：
   - 自动清理换行符和控制字符
   - 提取有效JSON部分
   - 备用解析机制
   - 详细的错误日志记录

## 数据库集成建议

返回的数据可以直接插入到对应的数据表中：

- `enhancedTitle` → `haigui_soup.soup_title`
- `progressTasks` → `haigui_soup_progress_task` 表相关字段
- `keyClues` → `soup_clue` 表相关字段
- `hostManual` → `haigui_soup.host_manual`

建议在插入前进行JSON格式验证和必要的数据清洗。