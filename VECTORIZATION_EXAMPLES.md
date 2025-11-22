# 海龟汤向量化使用示例

## 快速开始

### 1. 创建单个海龟汤并向量化

```java
// 创建海龟汤对象
HaiGuiSoup soup = new HaiGuiSoup();
soup.setSoupId(UUID.randomUUID().toString());
soup.setSoupTitle("双鱼玉佩的游戏");
soup.setSoupSurface("我在沙漠中遇到了一个奇怪的现象，那里有一个神秘的深渊，");
soup.setSoupBottom("这是一个关于复制体的故事。主角在1978年用望远镜看深渊，发现下面是雪地，");
soup.setHostManual("关键线索：村子里的女人都是复制体，深渊下面是复制体的尸体。");

// 向量化并存储
boolean success = haiGuiVectorService.vectorizeAndSaveSoup(soup);
if (success) {
    System.out.println("海龟汤向量化成功！");
}
```

### 2. 批量向量化（简化接口）

```java
// 准备简化请求数据
List<HaiGuiSoupController.SimpleSoupRequest> requests = Arrays.asList(
    createSimpleSoup("soup-001", "双鱼玉佩的游戏",
        "我在沙漠中遇到了一个奇怪的现象...",
        "这是一个关于复制体的故事..."),
    createSimpleSoup("soup-002", "时间倒流的小镇",
        "我发现了一个小镇，那里的人们都在重复过同一天...",
        "这是外星人的实验，他们想研究人类的行为模式...")
);

// 调用简化批量向量化接口
Result<String> result = haiGuiSoupController.batchVectorizeSimple(requests);
System.out.println(result.getMessage());
}

private static HaiGuiSoupController.SimpleSoupRequest createSimpleSoup(
    String id, String title, String surface, String bottom) {
    HaiGuiSoupController.SimpleSoupRequest request =
        new HaiGuiSoupController.SimpleSoupRequest();
    request.setSoupId(id);
    request.setSoupTitle(title);
    request.setSoupSurface(surface);
    request.setSoupBottom(bottom);
    request.setPlayCount(0);
    request.setCreatedAt(new Date());
    return request;
}
```

### 3. 基于问题智能搜索

```java
// 玩家提出问题
String question = "沙漠中的深渊下面有什么秘密？";

// 搜索相关海龟汤
Map<String, Double> searchResults = turtleSoupService.findMatchingSoup(question, 5);

// 输出搜索结果
searchResults.forEach((soupId, similarity) -> {
    System.out.printf("海龟汤ID: %s, 相似度: %.2f%n", soupId, similarity);
});
```

### 4. 获取海龟汤推荐

```java
// 基于用户玩过的海龟汤推荐相似内容
String soupId = "soup-001";
Map<String, Double> recommendations = turtleSoupService.recommendSoups(soupId, 3);

System.out.println("为您推荐相似的海龟汤：");
recommendations.forEach((id, score) -> {
    System.out.printf("推荐: %s (相似度: %.2f)%n", id, score);
});
```

## HTTP API 调用示例

### 1. 创建海龟汤

```bash
curl -X POST http://localhost:8080/api/haigui/soup/create \
  -H "Content-Type: application/json" \
  -d '{
    "soupTitle": "双鱼玉佩的游戏",
    "soupSurface": "我在沙漠中遇到了一个奇怪的现象，那里有一个神秘的深渊。",
    "soupBottom": "这是一个关于复制体的故事。村子里的女人都是复制体。",
    "hostManual": "关键线索：1. 村庄里很少见男性 2. 女人们晚上不出门",
    "keyClues": "[\"女人被限制出门\", \"深渊是复制体尸体\"]",
    "progressSettings": "{\"发现复制体\":50,\"找到真相\":100}"
  }'
```

响应示例：
```json
{
  "code": 200,
  "message": "海龟汤创建成功",
  "data": "123e4567-e89b-12d3-a456-426614174000"
}
```

### 2. 简化批量向量化

```bash
curl -X POST http://localhost:8080/api/haigui/soup/batch-vectorize-simple \
  -H "Content-Type: application/json" \
  -d '[
    {
      "soupId": "soup-001",
      "soupTitle": "双鱼玉佩的游戏",
      "soupSurface": "我在沙漠中遇到了一个奇怪的现象...",
      "soupBottom": "这是一个关于复制体的故事...",
      "playCount": 0
    },
    {
      "soupId": "soup-002",
      "soupTitle": "时间倒流的小镇",
      "soupSurface": "我发现了一个小镇，那里的人们都在重复过同一天...",
      "soupBottom": "这是外星人的实验，他们想研究人类的行为模式...",
      "playCount": 0
    }
  ]'
```

响应示例：
```json
{
  "code": 200,
  "message": "简化批量向量化完成: 成功 2/2",
  "data": null
}
```

### 3. 智能搜索

```bash
curl -X POST "http://localhost:8080/api/haigui/soup/search?question=沙漠中的神秘现象&topK=5"
```

响应示例：
```json
{
  "code": 200,
  "message": "搜索完成",
  "data": {
    "soup-001": 0.85,
    "soup-003": 0.72,
    "soup-005": 0.68
  }
}
```

### 4. 获取推荐

```bash
curl -X GET "http://localhost:8080/api/haigui/soup/recommend/soup-001?topK=3"
```

响应示例：
```json
{
  "code": 200,
  "message": "推荐获取成功",
  "data": {
    "soup-007": 0.89,
    "soup-012": 0.76,
    "soup-025": 0.71
  }
}
```

## 实际业务场景

### 场景1：游戏大厅智能匹配

```java
@Service
public class GameHallService {

    public List<HaiGuiSoup> recommendSoupsForUser(String userQuestion) {
        // 1. 使用向量化搜索找到相关海龟汤
        Map<String, Double> matchedSoups = haiGuiVectorService
            .searchSimilarSoups(userQuestion, 10);

        // 2. 获取海龟汤详细信息
        List<HaiGuiSoup> results = new ArrayList<>();
        matchedSoups.forEach((soupId, similarity) -> {
            HaiGuiSoup soup = getSoupById(soupId);
            if (soup != null && similarity > 0.5) { // 过滤低相似度结果
                results.add(soup);
            }
        });

        // 3. 按相似度排序
        results.sort((a, b) -> Double.compare(
            matchedSoups.get(b.getSoupId()),
            matchedSoups.get(a.getSoupId())
        ));

        return results;
    }
}
```

### 场景2：数据迁移 - 批量向量化现有数据

```java
@Component
public class DataMigrationService {

    public void migrateExistingSoups() {
        // 1. 从数据库获取未向量化的海龟汤
        List<HaiGuiSoup> existingSoups = soupRepository.findNotVectorizedSoups();

        // 2. 分批处理，避免内存压力
        int batchSize = 50;
        for (int i = 0; i < existingSoups.size(); i += batchSize) {
            List<HaiGuiSoup> batch = existingSoups.subList(i,
                Math.min(i + batchSize, existingSoups.size()));

            // 3. 批量向量化
            int successCount = haiGuiVectorService.batchVectorizeSoups(batch);

            log.info("迁移进度: {}/{} (成功: {})",
                i + batchSize, existingSoups.size(), successCount);

            // 4. 短暂休息，避免请求过于频繁
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

### 场景3：实时游戏问答

```java
@RestController
public class GameQuestionController {

    @PostMapping("/game/ask")
    public Result<List<HaiGuiSoup>> answerQuestion(
            @RequestParam String sessionId,
            @RequestParam String question) {

        // 1. 获取当前游戏的海龟汤信息
        GameSession session = gameSessionService.getSession(sessionId);
        String currentSoupId = session.getSoupId();

        // 2. 基于问题搜索相关线索
        Map<String, Double> relevantSoups = haiGuiVectorService
            .searchSimilarSoups(question, 3);

        // 3. 如果当前海龟汤匹配度高，返回线索
        if (relevantSoups.containsKey(currentSoupId) &&
            relevantSoups.get(currentSoupId) > 0.7) {

            List<String> relevantClues = getRelevantClues(question, currentSoupId);
            return Result.success("找到相关线索", convertCluesToSoups(relevantClues));
        }

        // 4. 否则推荐相似的海龟汤
        return Result.success("为您推荐相似的海龟汤",
            getSoupDetails(relevantSoups.keySet()));
    }
}
```

## 性能测试示例

### 批量向量化性能测试

```java
@Test
public void testBatchVectorizationPerformance() {
    // 准备测试数据
    List<HaiGuiSoup> testSoups = createTestSoups(1000);

    long startTime = System.currentTimeMillis();

    // 执行批量向量化
    int successCount = haiGuiVectorService.batchVectorizeSoups(testSoups);

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    // 输出性能指标
    System.out.printf("向量化 %d 个海龟汤耗时: %d ms%n", successCount, duration);
    System.out.printf("平均每个海龟汤耗时: %.2f ms%n",
        (double) duration / successCount);
    System.out.printf("成功率: %.2f%%%n",
        (double) successCount / testSoups.size() * 100);
}
```

### 搜索性能测试

```java
@Test
public void testSearchPerformance() {
    List<String> testQuestions = Arrays.asList(
        "沙漠中的神秘现象",
        "复制体是怎么回事",
        "时间倒流的小镇",
        "外星人的实验"
    );

    for (String question : testQuestions) {
        long startTime = System.nanoTime();

        Map<String, Double> results = haiGuiVectorService
            .searchSimilarSoups(question, 10);

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        System.out.printf("搜索 '%s' 耗时: %.2f ms, 结果数: %d%n",
            question, durationMs, results.size());
    }
}
```

## 常见问题处理

### 1. 向量化失败处理

```java
public boolean safeVectorizeSoup(HaiGuiSoup soup) {
    try {
        return haiGuiVectorService.vectorizeAndSaveSoup(soup);
    } catch (Exception e) {
        log.error("向量化失败: soupId={}, error={}", soup.getSoupId(), e.getMessage());

        // 记录失败的海龟汤，后续重试
        failedVectorizationRepository.save(soup.getSoupId(), e.getMessage());
        return false;
    }
}
```

### 2. 向量存储检查

```java
public boolean checkSoupVectorization(String soupId) {
    try {
        // 检查向量是否存在
        List<Float> surfaceVector = haiGuiVectorService.getSoupVector(soupId, "SURFACE");
        List<Float> bottomVector = haiGuiVectorService.getSoupVector(soupId, "BOTTOM");

        boolean isComplete = surfaceVector != null && !surfaceVector.isEmpty() &&
                            bottomVector != null && !bottomVector.isEmpty();

        if (!isComplete) {
            log.warn("海龟汤向量化不完整: soupId={}", soupId);
        }

        return isComplete;
    } catch (Exception e) {
        log.error("检查向量化状态失败: soupId={}", soupId, e);
        return false;
    }
}
```

这些示例展示了海龟汤向量化的实际应用场景和最佳实践，帮助你快速集成和使用向量化功能。