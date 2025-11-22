# 海龟汤向量化存储实现指南

## 概述

本文档介绍了海龟汤游戏向量化存储的完整实现方案，将海龟汤内容向量化后存储到Redis Stack中，支持智能搜索和推荐功能。

## 架构设计

### 1. 向量化流程

```
海龟汤内容 → BGE向量化 → Redis Stack存储 → 向量搜索
```

- **BGE向量化**: 使用BGE模型将文本转换为向量
- **Redis Stack**: 存储向量和元数据
- **向量搜索**: 基于余弦相似度进行智能匹配

### 2. 数据存储结构

#### Redis存储键设计

| 键名格式 | 类型 | 内容 |
|---------|------|------|
| `hai_gui:soup:{soupId}` | Hash | 海龟汤基本信息 |
| `hai_gui:vec:surface:{soupId}` | String | 汤面向量 |
| `hai_gui:vec:bottom:{soupId}` | String | 汤底向量 |
| `hai_gui:vec:manual:{soupId}` | String | 主持人手册向量 |
| `hai_gui:soups:all` | Set | 所有海龟汤ID集合 |

## 核心组件

### 1. BgeVectorClientUtil - 向量化客户端

负责调用BGE服务进行文本向量化：

```java
// 单个文本向量化
SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(text);
List<Float> vector = response.getEmbeddings().get(0);

// 批量文本向量化
BatchEncodeResponse response = BgeVectorClientUtil.encodeBatch(texts);
```

### 2. RedisStackClient - Redis存储客户端

提供向量的Redis存储和检索功能：

```java
// 存储完整海龟汤信息
redisStackClient.saveCompleteSoup(soup, surfaceVector, bottomVector);

// 搜索相似海龟汤
Map<String, Double> results = redisStackClient.searchSimilarSoups(queryVector, "SURFACE", topK);
```

### 3. HaiGuiVectorService - 向量化服务

整合向量化和存储的核心服务：

```java
// 向量化并存储海龟汤（标题+汤面组合向量化）
boolean success = haiGuiVectorService.vectorizeAndSaveSoup(soup);

// 批量向量化（只向量化核心内容，排除用户信息）
int successCount = haiGuiVectorService.batchVectorizeSoups(soupList);

// 基于问题搜索相似海龟汤
Map<String, Double> results = haiGuiVectorService.searchSimilarSoups(question, topK);
```

## 向量化策略

### 标题+汤面组合向量化

系统会自动将标题和汤面组合在一起进行向量化：

```text
标题：双鱼玉佩的游戏
汤面：我在沙漠中遇到了一个奇怪的现象，那里有一个神秘的深渊...
```

**优势**：
1. **语义更丰富**：标题提供了主题信息，汤面提供具体内容
2. **搜索更精准**：用户搜索时无论是用标题关键词还是汤面内容都能匹配
3. **上下文完整**：避免单独向量化导致的信息缺失

### 批量向量化优化

批量处理时会自动过滤不必要的用户信息：

```java
// 系统自动清理用户相关字段，只保留核心内容
HaiGuiSoup cleanSoup = createCleanSoupForVectorization(originalSoup);
```

**清理的字段**：
- `creator` - 创作者信息
- `uploader` - 上传者信息
- 其他非核心元数据

**保留的核心字段**：
- `soupId` - 海龟汤ID
- `soupTitle` - 标题
- `soupSurface` - 汤面
- `soupBottom` - 汤底
- `hostManual` - 主持人手册
- `keyClues` - 关键线索
- `progressSettings` - 进度设置

## API接口

### 1. 创建海龟汤

```http
POST /api/haigui/soup/create
Content-Type: application/json

{
  "soupTitle": "双鱼玉佩的游戏",
  "soupSurface": "我在沙漠中遇到了一个奇怪的现象...",
  "soupBottom": "这是一个关于复制体的故事...",
  "hostManual": "关键线索：...",
  "keyClues": "[\"线索1\", \"线索2\"]",
  "progressSettings": "{\"阶段一\":20,\"阶段二\":50}"
}
```

### 2. 搜索海龟汤

```http
POST /api/haigui/soup/search?question=沙漠中的神秘现象&topK=5
```

响应：
```json
{
  "code": 200,
  "message": "搜索完成",
  "data": {
    "soup1": 0.85,
    "soup2": 0.78,
    "soup3": 0.72
  }
}
```

### 3. 获取推荐

```http
GET /api/haigui/soup/recommend/{soupId}?topK=5
```

### 4. 批量向量化

#### 标准格式
```http
POST /api/haigui/soup/batch-vectorize
Content-Type: application/json

[
  {
    "soupId": "uuid1",
    "soupTitle": "标题1",
    "soupSurface": "汤面内容1",
    "soupBottom": "汤底内容1"
  },
  {
    "soupId": "uuid2",
    "soupTitle": "标题2",
    "soupSurface": "汤面内容2",
    "soupBottom": "汤底内容2"
  }
]
```

#### 简化格式（推荐）
```http
POST /api/haigui/soup/batch-vectorize-simple
Content-Type: application/json

[
  {
    "soupId": "uuid1",
    "soupTitle": "标题1",
    "soupSurface": "汤面内容1",
    "soupBottom": "汤底内容1",
    "hostManual": "主持人手册1",
    "playCount": 10
  },
  {
    "soupId": "uuid2",
    "soupTitle": "标题2",
    "soupSurface": "汤面内容2",
    "soupBottom": "汤底内容2",
    "hostManual": "主持人手册2",
    "playCount": 5
  }
]
```

## 使用场景

### 1. 智能问答

玩家提出问题时，系统可以：
1. 向量化玩家问题
2. 在汤面向量中搜索相似内容
3. 推荐最相关的海龟汤

```java
Map<String, Double> results = turtleSoupService.findMatchingSoup(
    "沙漠中遇到的奇怪现象", 10);
```

### 2. 内容推荐

基于用户玩过的海龟汤推荐相似内容：

```java
Map<String, Double> recommendations = turtleSoupService.recommendSoups(
    soupId, 5);
```

### 3. 批量处理

对现有的海龟汤进行批量向量化：

```java
List<HaiGuiSoup> soups = getAllExistingSoups();
int successCount = turtleSoupService.batchVectorizeSoups(soups);
```

## 配置要求

### 1. application.yml配置

```yaml
# BGE向量化服务
qingyou:
  BgeVector:
    host: http://your-bge-service:port/api/encode
  redis:
    host: your-redis-host
    port: 6379
    password: your-password
```

### 2. 依赖要求

确保以下依赖已正确配置：

```xml
<!-- Spring Boot Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Redis Lettuce客户端 -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

## 性能优化建议

### 1. 向量缓存

- 向量化结果存储在Redis中，避免重复计算
- 设置合适的过期时间（如30天）

### 2. 批量处理

- 批量向量化时添加适当的延迟，避免请求过于频繁
- 使用异步处理提升响应速度

### 3. 搜索优化

- 设置相似度阈值，过滤低相似度结果
- 限制搜索结果数量（topK）

### 4. 内存管理

- 向量数据较大，注意Redis内存使用
- 定期清理过期的向量数据

## 错误处理

### 1. 向量化失败

- 检查BGE服务连接
- 验证输入文本格式
- 记录详细错误日志

### 2. Redis连接失败

- 检查Redis服务状态
- 验证连接配置
- 实现重连机制

### 3. 向量维度不匹配

- 确保所有向量使用相同的模型生成
- 验证向量数据完整性

## 监控指标

### 1. 向量化性能

- 向量化响应时间
- 向量化成功率
- 批量处理速度

### 2. 搜索质量

- 搜索响应时间
- 搜索结果相关性
- 用户点击率

### 3. 系统健康度

- Redis连接状态
- BGE服务可用性
- 内存使用情况

## 扩展功能

### 1. 多模型支持

- 支持不同的向量化模型
- 模型切换机制
- A/B测试

### 2. 增量更新

- 新增海龟汤的实时向量化
- 修改内容的增量更新
- 版本管理

### 3. 高级搜索

- 多维度搜索（汤面+汤底）
- 权重调整
- 个性化推荐

## 总结

本方案实现了海龟汤内容的完整向量化存储，提供了：

1. **智能搜索**：基于向量相似度的精准匹配
2. **内容推荐**：根据用户喜好推荐相似内容
3. **高效存储**：利用Redis Stack的高性能存储
4. **易于扩展**：模块化设计，便于功能扩展

通过向量化技术，大大提升了海龟汤游戏的用户体验和内容发现效率。