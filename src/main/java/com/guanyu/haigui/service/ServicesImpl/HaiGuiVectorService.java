package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 海龟汤向量化服务
 * 负责海龟汤内容的向量化和Redis存储
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HaiGuiVectorService {

    private final RedisStackClient redisStackClient;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final SoupJsonParser soupJsonParser;

    /**
     * 向量化并存储海龟汤
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    public boolean vectorizeAndSaveSoup(HaiGuiSoup soup) {
        try {
            log.info("开始向量化海龟汤: soupId={}, title={}", soup.getSoupId(), soup.getSoupTitle());
            soup.setCreatedAt(LocalDateTime.now());

            // 1. 向量化标题+汤面（连接在一起）
            String titleAndSurface = combineTitleAndSurface(soup.getSoupTitle(), soup.getSoupSurface());
            List<Float> surfaceVector = vectorizeText(titleAndSurface);
            if (surfaceVector == null || surfaceVector.isEmpty()) {
                log.error("标题+汤面向量化失败: soupId={}", soup.getSoupId());
                return false;
            }

            // 2. 向量化汤底
            List<Float> bottomVector = vectorizeText(soup.getSoupBottom());
            if (bottomVector == null || bottomVector.isEmpty()) {
                log.error("汤底向量化失败: soupId={}", soup.getSoupId());
                return false;
            }

            // 4. 存储到Redis
            redisStackClient.saveCompleteSoup(soup, surfaceVector, bottomVector);

            // 5. 更新数据库中的向量键名
            soup.setSoupSurfaceVec(String.format("hai_gui:vec:surface:%s", soup.getSoupId()));
            soup.setSoupBottomVec(String.format("hai_gui:vec:bottom:%s", soup.getSoupId()));

            // 确保JSON格式正确
            if (soup.getKeyClues() == null || soup.getKeyClues().trim().isEmpty()) {
                soup.setKeyClues("[]");
            }
            // progress_settings字段已移除，现在存储在haigui_soup_progress_task表中
            // TODO: 如果需要，可以实现进度任务的创建逻辑

            log.info("海龟汤向量化完成: soupId={}, surfaceDim={}, bottomDim={}",
                    soup.getSoupId(), surfaceVector.size(), bottomVector.size());
            System.out.println("soup = " + soup);
            haiGuiSoupRepository.save(soup);
            return true;
        } catch (Exception e) {
            log.error("海龟汤向量化失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    /**
     * 批量向量化海龟汤（仅向量化核心内容，不包括用户信息）
     * @param soups 海龟汤列表
     * @return 成功数量
     */
    public int batchVectorizeSoups(List<HaiGuiSoup> soups) {
        int successCount = 0;

        for (HaiGuiSoup soup : soups) {
            try {
                // 重置用户信息，避免不必要的序列化
                HaiGuiSoup cleanSoup = createCleanSoupForVectorization(soup);

                if (vectorizeAndSaveSoup(cleanSoup)) {
                    successCount++;
                    log.info("批量向量化进度: {}/{} (soupId={})",
                            successCount, soups.size(), soup.getSoupId());
                } else {
                    log.error("海龟汤向量化失败: soupId={}", soup.getSoupId());
                }

                // 避免请求过于频繁，添加小延迟
                Thread.sleep(100);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("批量向量化被中断");
                break;
            } catch (Exception e) {
                log.error("批量向量化处理失败: soupId={}", soup.getSoupId(), e);
            }
        }

        log.info("批量向量化完成: total={}, success={}", soups.size(), successCount);
        return successCount;
    }

    /**
     * 创建用于向量化的纯净海龟汤对象（去除用户信息）
     * @param originalSoup 原始海龟汤对象
     * @return 纯净的海龟汤对象
     */
    private HaiGuiSoup createCleanSoupForVectorization(HaiGuiSoup originalSoup) {
        HaiGuiSoup cleanSoup = new HaiGuiSoup();

        // 复制核心字段
        cleanSoup.setSoupId(originalSoup.getSoupId());
        cleanSoup.setSoupTitle(originalSoup.getSoupTitle());
        cleanSoup.setSoupSurface(originalSoup.getSoupSurface());
        cleanSoup.setSoupBottom(originalSoup.getSoupBottom());
        cleanSoup.setHostManual(originalSoup.getHostManual());
        cleanSoup.setKeyClues(originalSoup.getKeyClues());
        // progressSettings字段已移除，不再设置
        cleanSoup.setPlayCount(originalSoup.getPlayCount());
        cleanSoup.setUploadTime(originalSoup.getUploadTime());
        cleanSoup.setCreatedAt(originalSoup.getCreatedAt());
        cleanSoup.setUpdatedAt(originalSoup.getUpdatedAt());
        cleanSoup.setIsDeleted(originalSoup.getIsDeleted());
        cleanSoup.setCreatorId(originalSoup.getCreatorId());
        cleanSoup.setUploaderId(originalSoup.getUploaderId());

        // 不设置creator和uploader字段，减少不必要的数据传输

        return cleanSoup;
    }

    /**
     * 组合标题和汤面文本
     * @param title 标题
     * @param surface 汤面
     * @return 组合后的文本
     */
    private String combineTitleAndSurface(String title, String surface) {
        StringBuilder combined = new StringBuilder();

        if (title != null && !title.trim().isEmpty()) {
            combined.append("标题：").append(title.trim()).append("\n");
        }

        if (surface != null && !surface.trim().isEmpty()) {
            combined.append("汤面：").append(surface.trim());
        }

        return combined.toString();
    }

    /**
     * 向量化单个文本
     * @param text 待向量化的文本
     * @return 向量数据
     */
    private List<Float> vectorizeText(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }

            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(text);
            List<List<Float>> embeddings = response.getEmbeddings();

            if (embeddings != null && !embeddings.isEmpty()) {
                return embeddings.get(0); // 取第一个向量
            }

            return null;

        } catch (Exception e) {
            log.error("文本向量化失败: text={}", text.substring(0, Math.min(text.length(), 100)), e);
            return null;
        }
    }

    /**
     * 基于玩家问题搜索相似海龟汤
     * @param question 玩家问题
     * @param topK 返回前K个结果
     * @return 相似海龟汤ID及其相似度分数
     */
    public Map<String, Double> searchSimilarSoups(String question, int topK) {
        try {
            log.info("开始向量搜索: question={}, topK={}", question, topK);

            // 1. 向量化玩家问题
            List<Float> questionVector = vectorizeText(question);
            if (questionVector == null || questionVector.isEmpty()) {
                log.error("问题向量化失败: question={}", question);
                return Map.of();
            }

            // 2. 在汤面向量中搜索
            Map<String, Double> results = redisStackClient.searchSimilarSoups(
                    questionVector, "SURFACE", topK);

            log.info("向量搜索完成: question={}, matchedCount={}", question, results.size());
            return results;

        } catch (Exception e) {
            log.error("向量搜索失败: question={}", question, e);
            return Map.of();
        }
    }



    /**
     * 更新海龟汤的向量数据
     * @param soup 更新后的海龟汤对象
     * @return 是否成功
     */
    public boolean updateSoupVectors(HaiGuiSoup soup) {
        try {
            log.info("开始更新海龟汤向量: soupId={}", soup.getSoupId());

            // 删除旧的向量数据
            redisStackClient.deleteSoup(soup.getSoupId());

            // 重新向量化和存储
            return vectorizeAndSaveSoup(soup);

        } catch (Exception e) {
            log.error("更新海龟汤向量失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    /**
     * 删除海龟汤的所有向量数据
     * @param soupId 海龟汤ID
     */
    public void deleteSoupVectors(String soupId) {
        try {
            log.info("开始删除海龟汤向量: soupId={}", soupId);
            redisStackClient.deleteSoup(soupId);
            haiGuiSoupRepository.deleteById(soupId);
            log.info("海龟汤向量删除完成: soupId={}", soupId);

        } catch (Exception e) {
            log.error("删除海龟汤向量失败: soupId={}", soupId, e);
        }
    }

    /**
     * 获取海龟汤的向量数据
     * @param soupId 海龟汤ID
     * @param vectorType 向量类型
     * @return 向量数据
     */
    public List<Float> getSoupVector(String soupId, String vectorType) {
        try {
            return redisStackClient.getSoupVector(soupId, vectorType);
        } catch (Exception e) {
            log.error("获取海龟汤向量失败: soupId={}, vectorType={}", soupId, vectorType, e);
            return null;
        }
    }
}