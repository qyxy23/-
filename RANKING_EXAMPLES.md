# 海龟汤榜单功能使用示例

## 快速开始

### 1. 获取热门TOP10榜单

```java
// 调用Service接口
List<HaiGuiRankingService.HotSoupItem> top10Soups =
    haiGuiRankingService.getTop10HotSoups();

// 输出结果
top10Soups.forEach(item -> {
    System.out.printf("排名: %d, 标题: %s, 热度: %.1f%n",
        item.getRank(), item.getTitle(), item.getHotnessScore());
});
```

### 2. 记录用户行为

```java
// 记录用户玩海龟汤
turtleSoupService.recordPlayAction("soup-001");

// 记录用户点赞
turtleSoupService.recordLikeAction("soup-001");

// 记录用户分享
turtleSoupService.recordShareAction("soup-001");

// 记录用户评论
turtleSoupService.recordCommentAction("soup-001");
```

### 3. 获取海龟汤排名信息

```java
// 获取特定海龟汤的排名
HaiGuiRankingService.SoupRankInfo rankInfo =
    haiGuiRankingService.getSoupRankInfo("soup-001");

if (rankInfo.isInTop10()) {
    System.out.printf("恭喜！该海龟汤目前排名第%d，热度分数：%.1f%n",
        rankInfo.getCurrentRank(), rankInfo.getHotnessScore());
} else {
    System.out.println("该海龟汤暂未进入TOP10榜单");
}
```

## HTTP API调用示例

### 1. 获取热门TOP10

```bash
curl -X GET http://localhost:8080/api/haigui/ranking/top10

# 响应示例
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
    }
  ]
}
```

### 2. 获取近期热门（最近7天前15名）

```bash
curl -X GET "http://localhost:8080/api/haigui/ranking/recent-hot?days=7&topN=15"
```

### 3. 记录用户行为

```bash
# 记录播放行为
curl -X POST "http://localhost:8080/api/haigui/ranking/record-action?soupId=soup-001&action=play"

# 记录点赞行为
curl -X POST "http://localhost:8080/api/haigui/ranking/record-action?soupId=soup-001&action=like"

# 记录分享行为
curl -X POST "http://localhost:8080/api/haigui/ranking/record-action?soupId=soup-001&action=share"

# 记录评论行为
curl -X POST "http://localhost:8080/api/haigui/ranking/record-action?soupId=soup-001&action=comment"
```

### 4. 获取海龟汤排名

```bash
curl -X GET http://localhost:8080/api/haigui/ranking/soup-rank/soup-001
```

### 5. 获取榜单统计信息

```bash
curl -X GET http://localhost:8080/api/haigui/ranking/statistics
```

### 6. 获取所有榜单数据

```bash
curl -X GET "http://localhost:8080/api/haigui/ranking/all-rankings?days=7&topN=10"
```

## 业务场景集成

### 1. 游戏开始时记录行为

```java
@RestController
public class GameController {

    @Autowired
    private TurtleSoupService turtleSoupService;

    @PostMapping("/game/start")
    public Result<GameSession> startGame(@RequestParam String soupId) {
        // 开始游戏逻辑
        GameSession session = gameService.startGame(soupId);

        // 自动记录播放行为
        turtleSoupService.recordPlayAction(soupId);

        return Result.success("游戏开始", session);
    }
}
```

### 2. 在游戏详情页显示排名

```java
@Service
public class SoupDetailService {

    public SoupDetailVO getSoupDetail(String soupId) {
        // 获取海龟汤基本信息
        HaiGuiSoup soup = soupRepository.findById(soupId);

        // 获取排名信息
        HaiGuiRankingService.SoupRankInfo rankInfo =
            turtleSoupService.getSoupRankInfo(soupId);

        return SoupDetailVO.builder()
                .soup(soup)
                .currentRank(rankInfo.getCurrentRank())
                .isInTop10(rankInfo.isInTop10())
                .hotnessScore(rankInfo.getHotnessScore())
                .build();
    }
}
```

### 3. 首页榜单展示

```java
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        // 获取热门TOP10
        List<HaiGuiRankingService.HotSoupItem> top10Soups =
            haiGuiRankingService.getTop10HotSoups();

        // 获取近期热门
        List<HaiGuiRankingService.HotSoupItem> recentHot =
            haiGuiRankingService.getRecentHotSoups(7, 10);

        model.addAttribute("top10Soups", top10Soups);
        model.addAttribute("recentHot", recentHot);

        return "home";
    }
}
```

### 4. 定时清理过期数据

```java
@Component
public class RankingScheduledTasks {

    @Autowired
    private HaiGuiRankingService haiGuiRankingService;

    // 每天凌晨2点清理过期数据
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        log.info("开始清理过期热度数据");
        haiGuiRankingService.cleanupExpiredData();
        log.info("过期热度数据清理完成");
    }

    // 每小时更新统计数据
    @Scheduled(cron = "0 0 * * * ?")
    public void updateStatistics() {
        HaiGuiRankingService.RankingStatistics stats =
            haiGuiRankingService.getRankingStatistics();

        // 发送统计信息到监控系统
        monitoringService.reportRankingStats(stats);
    }
}
```

## 前端集成示例

### 1. React组件 - 热门榜单

```jsx
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function HotRanking() {
    const [top10Soups, setTop10Soups] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchTop10Soups();
    }, []);

    const fetchTop10Soups = async () => {
        try {
            setLoading(true);
            const response = await axios.get('/api/haigui/ranking/top10');
            setTop10Soups(response.data.data);
        } catch (error) {
            console.error('获取热门榜单失败:', error);
        } finally {
            setLoading(false);
        }
    };

    const handlePlayAction = async (soupId) => {
        try {
            await axios.post(`/api/haigui/ranking/record-action?soupId=${soupId}&action=play`);
            // 重新获取榜单数据
            fetchTop10Soups();
        } catch (error) {
            console.error('记录播放行为失败:', error);
        }
    };

    if (loading) {
        return <div>加载中...</div>;
    }

    return (
        <div className="hot-ranking">
            <h2>🔥 热门TOP10</h2>
            <ul className="ranking-list">
                {top10Soups.map((item, index) => (
                    <li key={item.soupId} className="ranking-item">
                        <div className="rank-number">#{item.rank}</div>
                        <div className="soup-info">
                            <h3 className="soup-title">{item.title}</h3>
                            <p className="soup-surface">{item.surface}</p>
                            <div className="soup-stats">
                                <span>播放: {item.playCount}</span>
                                <span>热度: {item.hotnessScore.toFixed(1)}</span>
                            </div>
                        </div>
                        <button
                            onClick={() => handlePlayAction(item.soupId)}
                            className="play-btn"
                        >
                            开始游戏
                        </button>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default HotRanking;
```

### 2. Vue组件 - 榜单页面

```vue
<template>
  <div class="ranking-page">
    <div class="ranking-section">
      <h2>🔥 热门TOP10</h2>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else class="ranking-list">
        <div
          v-for="item in top10Soups"
          :key="item.soupId"
          class="ranking-item"
        >
          <div class="rank-badge">TOP {{ item.rank }}</div>
          <div class="soup-content">
            <h3>{{ item.title }}</h3>
            <p>{{ item.surface }}</p>
            <div class="stats">
              <span>👁 {{ item.playCount }}</span>
              <span>🔥 {{ item.hotnessScore.toFixed(0) }}</span>
            </div>
          </div>
          <button @click="playSoup(item.soupId)" class="play-btn">
            开始游戏
          </button>
        </div>
      </div>
    </div>

    <div class="ranking-section">
      <h2>📈 近期热门（最近7天）</h2>
      <div class="ranking-list">
        <div
          v-for="item in recentHot"
          :key="item.soupId"
          class="ranking-item"
        >
          <div class="rank-badge"># {{ item.rank }}</div>
          <div class="soup-content">
            <h3>{{ item.title }}</h3>
            <div class="stats">
              <span>🔥 {{ item.hotnessScore.toFixed(0) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'RankingPage',
  data() {
    return {
      loading: true,
      top10Soups: [],
      recentHot: []
    };
  },
  async mounted() {
    await this.fetchRankingData();
  },
  methods: {
    async fetchRankingData() {
      try {
        this.loading = true;

        // 一次性获取所有榜单数据
        const response = await axios.get('/api/haigui/ranking/all-rankings');
        const { top10, recentHot } = response.data.data;

        this.top10Soups = top10;
        this.recentHot = recentHot;
      } catch (error) {
        console.error('获取榜单数据失败:', error);
      } finally {
        this.loading = false;
      }
    },

    async playSoup(soupId) {
      try {
        // 记录播放行为
        await axios.post(`/api/haigui/ranking/record-action?soupId=${soupId}&action=play`);

        // 跳转到游戏页面
        this.$router.push(`/game/${soupId}`);
      } catch (error) {
        console.error('开始游戏失败:', error);
      }
    }
  }
};
</script>
```

## 测试用例

### 1. 单元测试

```java
@SpringBootTest
class HaiGuiRankingServiceTest {

    @Autowired
    private HaiGuiRankingService haiGuiRankingService;

    @Test
    void testRecordUserAction() {
        // 记录用户行为
        haiGuiRankingService.recordUserAction("test-soup-001", 1L, "play");

        // 验证热度是否正确增加
        Double hotness = haiGuiRankingService.getSoupRankInfo("test-soup-001").getHotnessScore();
        assertEquals(10.0, hotness, 0.01);
    }

    @Test
    void testGetTop10HotSoups() {
        // 创建测试数据
        createTestRankingData();

        // 获取热门TOP10
        List<HaiGuiRankingService.HotSoupItem> top10 = haiGuiRankingService.getTop10HotSoups();

        // 验证结果
        assertFalse(top10.isEmpty());
        assertTrue(top10.size() <= 10);

        // 验证排名顺序
        for (int i = 0; i < top10.size() - 1; i++) {
            assertTrue(top10.get(i).getHotnessScore() >= top10.get(i + 1).getHotnessScore());
        }
    }
}
```

### 2. 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testGetTop10Endpoint() {
        ResponseEntity<Result> response = restTemplate.getForEntity(
            "/api/haigui/ranking/top10", Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
    }

    @Test
    void testRecordActionEndpoint() {
        ResponseEntity<Result> response = restTemplate.postForEntity(
            "/api/haigui/ranking/record-action?soupId=test-soup&action=play",
            null, Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

## 性能测试

### 1. 并发测试

```java
@Test
void testConcurrentActionRecording() throws InterruptedException {
    int threadCount = 100;
    int actionsPerThread = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        final int threadId = i;
        executor.submit(() -> {
            try {
                for (int j = 0; j < actionsPerThread; j++) {
                    haiGuiRankingService.recordUserAction(
                        "test-soup-" + (threadId * actionsPerThread + j),
                        (long) threadId, "play");
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(30, TimeUnit.SECONDS);
    executor.shutdown();
}
```

这些示例展示了海龟汤榜单功能的完整使用方式，从基础的API调用到复杂的前端集成，帮助快速上手和扩展功能。