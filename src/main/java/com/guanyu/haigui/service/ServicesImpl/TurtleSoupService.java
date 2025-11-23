package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.bean.BeanUtil;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.ProgressRule;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.HaiGuiRankingService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SoupJsonParser soupJsonParser;
    private final com.guanyu.haigui.repository.HaiGuiSoupRepository haiGuiSoupRepository;

    /**
     * 新增海龟汤（包含向量化处理和智能线索解析）
     *
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    @Transactional
    public boolean addTurtleSoup(CreateHaiGuiSoupDTO soup) {
        try {
            // 设置创建时间等必要字段
            HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
            haiGuiSoup.setSoupId(java.util.UUID.randomUUID().toString());
            haiGuiSoup.setPlayCount(0);
            haiGuiSoup.setUploadTime(java.time.LocalDateTime.now());

            // 复制基本属性
            haiGuiSoup.setSoupTitle(soup.getSoupTitle());
            haiGuiSoup.setSoupSurface(soup.getSoupSurface());
            haiGuiSoup.setSoupBottom(soup.getSoupBottom());
            haiGuiSoup.setHostManual(soup.getHostManual());

            // 解析线索和进度设置
            String keyCluesInput = soup.getKeyCluesAsString();
            String progressSettingsInput = soup.getProgressSettingsAsString();

            log.info("原始keyClues输入类型: {}, 值: '{}'",
                    soup.getKeyClues() != null ? soup.getKeyClues().getClass().getSimpleName() : "null",
                    keyCluesInput);

            // 解析线索列表（使用GameClue业务实体）
            List<GameClue> clues = soupJsonParser.parseKeyClues(keyCluesInput);

            log.info("解析得到的线索数量: {}", clues.size());

            // 将GameClue列表序列化为JSON存储
            String cluesJson = soupJsonParser.serializeClues(clues);
            haiGuiSoup.setKeyClues(cluesJson);

            log.info("序列化后的线索JSON: '{}'", cluesJson);

            // 设置进度设置JSON
            haiGuiSoup.setProgressSettings(soupJsonParser.serializeProgressRule(
                    soupJsonParser.parseProgressSettings(progressSettingsInput)
            ));

            log.info("智能解析结果 - 线索数量: {}, 进度设置: {}",
                    clues.size(),
                    soupJsonParser.parseProgressSettings(progressSettingsInput).getDifficulty());

            UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                    .orElseThrow(() -> new BusinessException(404, "用户不存在"));
            haiGuiSoup.setUploaderId(userInfo.getUserId());
            haiGuiSoup.setCreatorId(userInfo.getUserId());
            haiGuiSoup.setUploader(userInfo);
            haiGuiSoup.setCreator(userInfo);

            log.info("开始新增海龟汤: title={}", soup.getSoupTitle());

            // 1. 向量化并存储到Redis
            boolean vectorSuccess = haiGuiVectorService.vectorizeAndSaveSoup(haiGuiSoup);
            if (!vectorSuccess) {
                log.error("海龟汤向量化失败: soupId={}", haiGuiSoup.getSoupId());
                return false;
            }

            log.info("海龟汤新增成功: soupId={}, 线索数量: {}", haiGuiSoup.getSoupId(), clues.size());
            return true;

        } catch (Exception e) {
            log.error("新增海龟汤失败: title={}", soup.getSoupTitle(), e);
            throw new BusinessException(500, "新增海龟汤失败: " + e.getMessage());
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
     * 获取海龟汤的线索（从JSON解析为GameClue列表）
     *
     * @param soupId 海龟汤ID
     * @return 线索列表
     */
    public List<GameClue> getSoupClues(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null || soup.getKeyClues() == null) {
                return List.of();
            }

            List<GameClue> clues = soupJsonParser.parseKeyClues(soup.getKeyClues());
            log.info("获取海龟汤线索成功: soupId={}, 线索数量={}", soupId, clues.size());
            return clues;
        } catch (Exception e) {
            log.error("获取海龟汤线索失败: soupId={}", soupId, e);
            return List.of();
        }
    }

    /**
     * 获取海龟汤的关键线索
     *
     * @param soupId 海龟汤ID
     * @return 关键线索列表
     */
    public List<GameClue> getSoupKeyClues(String soupId) {
        try {
            List<GameClue> allClues = getSoupClues(soupId);
            List<GameClue> keyClues = allClues.stream()
                    .filter(GameClue::isKeyClue)
                    .toList();
            log.info("获取海龟汤关键线索成功: soupId={}, 关键线索数量={}", soupId, keyClues.size());
            return keyClues;
        } catch (Exception e) {
            log.error("获取海龟汤关键线索失败: soupId={}", soupId, e);
            return List.of();
        }
    }

    /**
     * 根据线索类型获取海龟汤线索
     *
     * @param soupId 海龟汤ID
     * @param clueType 线索类型
     * @return 指定类型的线索列表
     */
    public List<GameClue> getSoupCluesByType(String soupId, com.guanyu.haigui.Enum.ClueType clueType) {
        try {
            List<GameClue> allClues = getSoupClues(soupId);
            List<GameClue> filteredClues = allClues.stream()
                    .filter(clue -> clueType.equals(clue.getClueType()))
                    .toList();
            log.info("获取海龟汤线索成功: soupId={}, 类型={}, 线索数量={}", soupId, clueType, filteredClues.size());
            return filteredClues;
        } catch (Exception e) {
            log.error("获取海龟汤线索失败: soupId={}, 类型={}", soupId, clueType, e);
            return List.of();
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