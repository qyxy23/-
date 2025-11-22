# 海龟汤榜单功能实现指南

## 概述

海龟汤榜单功能提供了完整的热点数据统计和排行榜系统，支持基于用户行为的热度计算、多种榜单展示，以及实时排名更新。

## 核心功能

### 1. 热度计算机制

#### 热度分数组成
- **播放次数**：每次播放 +10 热度
- **点赞行为**：每次点赞 +20 热度
- **分享行为**：每次分享 +50 热度
- **评论行为**：每次评论 +15 热度

#### 数据存储结构
```
hai_gui:hotness:soups          # ZSET - 总热度排行榜
hai_gui:daily:plays:{date}     # ZSET - 每日播放统计
hai_gui:user:{userId}:actions  # HASH - 用户行为统计
```

### 2. Redis排行榜设计

#### 有序集合(ZSET)应用
- 使用ZSET存储热度分数，支持高效的范围查询
- `ZREVRANGE` 获取排名前N的热门内容
- `ZINCRBY` 实时更新热度分数
- 自动设置过期时间，避免内存无限增长

#### 热度更新流程
```
用户行为 → 增加热度分数 → 更新排行榜 → 记录用户行为统计
```

## API接口

### 1. 获取热门TOP10榜单

```http
GET /api/haigui/ranking/top10
```

**响应示例**：
```json
{
  "code": 200,
  "message": "获取热门TOP10成功",
  "data": [
    {
      "rank": 1,
      "soupId": "soup-001",
      "title": "双鱼玉佩的游戏",
      "surface": "我在沙漠中遇到了一个奇怪的现象...",
      "playCount": 1250,
      "hotnessScore": 8750.0,
      "createdAt": "2024-01-15T10:30:00.000+00:00"
    },
    {
      "rank": 2,
      "soupId": "soup-002",
      "title": "时间倒流的小镇",
      "surface": "我发现了一个小镇，那里的人们都在重复过同一天...",
      "playCount": 980,
      "hotnessScore": 6520.0,
      "createdAt": "2024-01-14T15:45:00.000+00:00"
    }
  ]
}
```

### 2. 获取近期热门榜单

```http
GET /api/haigui/ranking/recent-hot?days=7&topN=15
```

**参数说明**：
- `days`: 近期天数（1-30，默认7天）
- `topN`: 返回前N名（1-50，默认10名）

### 3. 记录用户行为

```http
POST /api/haigui/ranking/record-action?soupId=soup-001&action=play
```

**支持的行为类型**：
- `play`: 播放/玩游戏
- `like`: 点赞
- `share`: 分享
- `comment`: 评论

### 4. 获取海龟汤排名信息

```http
GET /api/haigui/ranking/soup-rank/soup-001
```

**响应示例**：
```json
{
  "code": 200,
  "message": "获取排名信息成功",
  "data": {
    "soupId": "soup-001",
    "currentRank": 3,
    "hotnessScore": 5420.0,
    "isInTop10": true
  }
}
```

### 5. 获取榜单统计信息

```http
GET /api/haigui/ranking/statistics
```

**响应示例**：
```json
{
  "code": 200,
  "message": "获取统计信息成功",
  "data": {
    "totalRankedSoups": 156,
    "top10TotalHotness": 45680.0,
    "totalHotness": 125420.0,
    "top10Percentage": 36.4
  }
}
```

### 6. 一次性获取所有榜单数据

```http
GET /api/haigui/ranking/all-rankings?days=7&topN=10
```

**响应包含**：
- `top10`: 热门TOP10榜单
- `recentHot`: 近期热门榜单
- `statistics`: 榜单统计信息

## 业务集成

### 1. 游戏开始时记录播放行为

```java
@RestController
public class GameController {

    @PostMapping("/game/start")
    public Result<String> startGame(@RequestParam String soupId) {
        // 开始游戏逻辑...

        // 记录用户播放行为
        turtleSoupService.recordPlayAction(soupId);

        return Result.success("游戏开始");
    }
}
```

### 2. 用户操作时记录行为

```java
@Service
public class UserActionService {

    public void handleLikeAction(String soupId) {
        turtleSoupService.recordLikeAction(soupId);
        // 其他点赞逻辑...
    }

    public void handleShareAction(String soupId) {
        turtleSoupService.recordShareAction(soupId);
        // 其他分享逻辑...
    }

    public void handleCommentAction(String soupId) {
        turtleSoupService.recordCommentAction(soupId);
        // 其他评论逻辑...
    }
}
```

### 3. 获取海龟汤排名展示

```java
@Service
public class SoupDetailService {

    public SoupDetailResponse getSoupDetail(String soupId) {
        HaiGuiSoup soup = getSoupById(soupId);

        // 获取排名信息
        HaiGuiRankingService.SoupRankInfo rankInfo =
            turtleSoupService.getSoupRankInfo(soupId);

        return SoupDetailResponse.builder()
                .soup(soup)
                .rankInfo(rankInfo)
                .build();
    }
}
```

## 定时任务

### 1. 清理过期数据

```java
@Component
public class RankingScheduledTasks {

    private final HaiGuiRankingService haiGuiRankingService;

    // 每天凌晨2点清理过期数据
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        log.info("开始清理过期热度数据");
        haiGuiRankingService.cleanupExpiredData();
        log.info("过期热度数据清理完成");
    }
}
```

### 2. 数据统计任务

```java
@Component
public class RankingStatisticsTasks {

    private final HaiGuiRankingService haiGuiRankingService;

    // 每小时更新热度统计
    @Scheduled(cron = "0 0 * * * ?")
    public void updateStatistics() {
        log.info("更新热度统计信息");
        HaiGuiRankingService.RankingStatistics stats =
            haiGuiRankingService.getRankingStatistics();

        // 发送统计信息到监控系统
        monitoringService.reportRankingStats(stats);
    }
}
```

## 性能优化

### 1. Redis优化策略

#### 数据结构选择
- **ZSET排行榜**：O(log(N))复杂度的插入和查询
- **Hash用户行为**：O(1)复杂度的读写
- **过期策略**：自动清理过期数据，控制内存使用

#### 批量操作
```java
public void batchUpdateHotness(List<SoupHotnessUpdate> updates) {
    for (SoupHotnessUpdate update : updates) {
        redisStackClient.incrementSoupHotness(
            update.getSoupId(), update.getIncrement());
    }
}
```

### 2. 缓存策略

#### 本地缓存热门数据
```java
@Cacheable(value = "hotSoups", key = "'top10'", cacheManager = "redisCacheManager")
public List<HotSoupItem> getTop10HotSoups() {
    return haiGuiRankingService.getTop10HotSoups();
}
```

#### 异步更新热度
```java
@Async
public void recordUserActionAsync(String soupId, Long userId, String action) {
    haiGuiRankingService.recordUserAction(soupId, userId, action);
}
```

## 监控和告警

### 1. 关键指标监控

- 热门TOP10更新频率
- 用户行为记录成功率
- Redis内存使用情况
- API响应时间

### 2. 告警规则

```yaml
alerts:
  - name: "热门榜单更新延迟"
    condition: "ranking_update_time > 5000ms"

  - name: "用户行为记录失败率过高"
    condition: "action_record_failure_rate > 5%"

  - name: "Redis排行榜数据异常"
    condition: "ranking_zset_size == 0"
```

## 故障处理

### 1. Redis连接异常

```java
@Retryable(value = {RedisConnectionException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void recordUserAction(String soupId, Long userId, String action) {
    haiGuiRankingService.recordUserAction(soupId, userId, action);
}
```

### 2. 数据一致性修复

```java
public void repairHotnessData() {
    // 重新计算所有海龟汤的热度分数
    List<HaiGuiSoup> allSoups = soupRepository.findAll();

    for (HaiGuiSoup soup : allSoups) {
        int playCount = soup.getPlayCount();
        int likeCount = soup.getLikeCount();
        int shareCount = soup.getShareCount();
        int commentCount = soup.getCommentCount();

        haiGuiRankingService.updateSoupHotness(
            soup.getSoupId(), playCount, likeCount, shareCount, commentCount);
    }
}
```

## 扩展功能

### 1. 多维度排行榜

```java
// 可以扩展支持更多维度的排行榜
public Map<String, Double> getRankingByCategory(String category, int topN) {
    String rankingKey = "hai_gui:ranking:" + category;
    return redisStackClient.getCommands()
        .zrevrangeWithScores(rankingKey, 0, topN - 1)
        .stream()
        .collect(Collectors.toMap(
            tuple -> tuple.getValue(),
            tuple -> tuple.getScore(),
            (oldValue, newValue) -> oldValue,
            LinkedHashMap::new
        ));
}
```

### 2. 时间窗口排行榜

```java
// 支持不同时间窗口的排行榜
public List<HotSoupItem> getTimeWindowRanking(String timeWindow, int topN) {
    switch (timeWindow) {
        case "today":
            return getTodayRanking(topN);
        case "week":
            return getWeeklyRanking(topN);
        case "month":
            return getMonthlyRanking(topN);
        default:
            return getTop10HotSoups();
    }
}
```

## 总结

海龟汤榜单功能提供了：

1. **实时热度计算**：基于用户行为的多维度热度计算
2. **高效排行榜**：基于Redis ZSET的高性能排行榜
3. **多样化榜单**：支持TOP10、近期热门、统计信息等多种展示
4. **自动数据管理**：过期数据清理、定时统计更新
5. **高可用性**：异常处理、重试机制、数据修复

通过这个系统，用户可以发现最热门的海龟汤内容，提升平台的活跃度和用户参与度。