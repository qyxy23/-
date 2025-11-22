package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.bean.BeanUtil;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.HaiGuiRankingService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 海龟汤服务实现类（重构版）
 * 集成向量化功能，提供智能搜索和推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurtleSoupService {

    private final HaiGuiVectorService haiGuiVectorService;
    private final RedisStackClient redisClient;
    private final BgeVectorClientUtil vectorClient;
    private final UserInfoRepository userInfoRepository;
    private final HaiGuiRankingService haiGuiRankingService;

    /**
     * 新增海龟汤（包含向量化处理）
     *
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    public boolean addTurtleSoup(CreateHaiGuiSoupDTO soup) {
        try {
            // 设置创建时间等必要字段
            HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
            haiGuiSoup.setSoupId(java.util.UUID.randomUUID().toString());
            BeanUtil.copyProperties(soup, haiGuiSoup);
            UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId()).orElseThrow(() -> new BusinessException(404, "用户不存在"));
            haiGuiSoup.setUploader(userInfo);
            haiGuiSoup.setCreator(userInfo);
            log.info("开始新增海龟汤: title={}", soup.getSoupTitle());

            // 1. 向量化并存储到Redis
            boolean vectorSuccess = haiGuiVectorService.vectorizeAndSaveSoup(haiGuiSoup);
            if (!vectorSuccess) {
                log.error("海龟汤向量化失败: soupId={}", haiGuiSoup.getSoupId());
                return false;
            }

            log.info("海龟汤新增成功: soupId={}", haiGuiSoup.getSoupId());
            return true;

        } catch (Exception e) {
            log.error("新增海龟汤失败: title={}", soup.getSoupTitle(), e);
            return false;
        }
    }

    /**
     * 根据玩家问题检索相似海龟汤（向量搜索）
     *
     * @param question 玩家输入的问题
     * @param topK     返回前K个结果
     * @return 匹配的海龟汤ID及其相似度分数
     */
    public Map<String, Double> findMatchingSoup(String question, int topK) {
        try {
            log.info("开始搜索相似海龟汤: question={}, topK={}", question, topK);
            return haiGuiVectorService.searchSimilarSoups(question, topK);

        } catch (Exception e) {
            log.error("搜索相似海龟汤失败: question={}", question, e);
            return Map.of();
        }
    }

    /**
     * 更新海龟汤信息（包含向量更新）
     *
     * @param soup 更新后的海龟汤对象
     * @return 是否成功
     */
    public boolean updateTurtleSoup(HaiGuiSoup soup) {
        try {
            log.info("开始更新海龟汤: soupId={}", soup.getSoupId());

            // 更新向量数据
            boolean updateSuccess = haiGuiVectorService.updateSoupVectors(soup);
            if (!updateSuccess) {
                log.error("更新海龟汤向量失败: soupId={}", soup.getSoupId());
                return false;
            }

            log.info("海龟汤更新成功: soupId={}", soup.getSoupId());
            return true;

        } catch (Exception e) {
            log.error("更新海龟汤失败: soupId={}", soup.getSoupId(), e);
            return false;
        }
    }

    /**
     * 删除海龟汤（包含向量数据清理）
     *
     * @param soupId 海龟汤ID
     * @return 是否成功
     */
    public boolean deleteTurtleSoup(String soupId) {
        try {
            log.info("开始删除海龟汤: soupId={}", soupId);

            // 删除向量数据
            haiGuiVectorService.deleteSoupVectors(soupId);

            log.info("海龟汤删除成功: soupId={}", soupId);
            return true;

        } catch (Exception e) {
            log.error("删除海龟汤失败: soupId={}", soupId, e);
            return false;
        }
    }

    /**
     * 批量向量化现有海龟汤
     *
     * @param soups 海龟汤列表
     * @return 成功数量
     */
    public int batchVectorizeSoups(List<HaiGuiSoup> soups) {
        log.info("开始批量向量化海龟汤: count={}", soups.size());
        return haiGuiVectorService.batchVectorizeSoups(soups);
    }

    /**
     * 获取海龟汤推荐（基于向量相似度）
     *
     * @param soupId 基准海龟汤ID
     * @param topK   推荐数量
     * @return 推荐的海龟汤ID及其相似度分数
     */
    public Map<String, Double> recommendSoups(String soupId, int topK) {
        try {
            log.info("开始获取海龟汤推荐: soupId={}, topK={}", soupId, topK);

            // 获取基准海龟汤的汤面向量
            List<Float> soupVector = haiGuiVectorService.getSoupVector(soupId, "SURFACE");
            if (soupVector == null || soupVector.isEmpty()) {
                log.error("获取基准海龟汤向量失败: soupId={}", soupId);
                return Map.of();
            }

            // 基于向量搜索推荐相似海龟汤
            return redisClient.searchSimilarSoups(soupVector, "SURFACE", topK);

        } catch (Exception e) {
            log.error("获取海龟汤推荐失败: soupId={}", soupId, e);
            return Map.of();
        }
    }

    /**
     * 检查海龟汤是否已向量化
     *
     * @param soupId 海龟汤ID
     * @return 是否已向量化
     */
    public boolean isSoupVectorized(String soupId) {
        try {
            List<Float> surfaceVector = haiGuiVectorService.getSoupVector(soupId, "SURFACE");
            List<Float> bottomVector = haiGuiVectorService.getSoupVector(soupId, "BOTTOM");
            return surfaceVector != null && !surfaceVector.isEmpty()
                    && bottomVector != null && !bottomVector.isEmpty();

        } catch (Exception e) {
            log.error("检查海龟汤向量化状态失败: soupId={}", soupId, e);
            return false;
        }
    }

    /**
     * 记录用户玩海龟汤的行为（自动记录播放行为）
     *
     * @param soupId 海龟汤ID
     */
    public void recordPlayAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "play");
            log.info("记录用户播放行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户播放行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户点赞行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordLikeAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "like");
            log.info("记录用户点赞行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户点赞行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户分享行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordShareAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "share");
            log.info("记录用户分享行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户分享行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 记录用户评论行为
     *
     * @param soupId 海龟汤ID
     */
    public void recordCommentAction(String soupId) {
        try {
            Long userId = BaseContext.getCurrentId();
            haiGuiRankingService.recordUserAction(soupId, userId, "comment");
            log.info("记录用户评论行为: userId={}, soupId={}", userId, soupId);

        } catch (Exception e) {
            log.error("记录用户评论行为失败: soupId={}", soupId, e);
        }
    }

    /**
     * 获取海龟汤热度排名
     *
     * @param soupId 海龟汤ID
     * @return 排名信息
     */
    public SoupRankInfo getSoupRankInfo(String soupId) {
        return haiGuiRankingService.getSoupRankInfo(soupId);
    }
}