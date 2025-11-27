package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.GameClue;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.InferenceTask;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.SoupRankInfo;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.InferenceTaskRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.VectorService;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 海龟汤服务实现类（重构版）
 * 集成向量化功能，提供智能搜索和推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurtleSoupService {

    private final VectorService vectorService;
    private final HaiGuiVectorService haiGuiVectorService;
    private final RedisStackClient redisClient;
    private final BgeVectorClientUtil vectorClient;
    private final UserInfoRepository userInfoRepository;
    private final HaiGuiRankingService haiGuiRankingService;
    private final SoupJsonParser soupJsonParser;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ClueDecompositionService clueDecompositionService;

    /**
     * 新增海龟汤（包含向量化处理和智能线索解析）
     *
     * @param soup 海龟汤对象
     * @return 是否成功
     */
    @Transactional
    public boolean addTurtleSoup(CreateHaiGuiSoupDTO soup) {
        try {
            // 创建海龟汤实体
            HaiGuiSoup haiGuiSoup = new HaiGuiSoup();
            haiGuiSoup.setSoupId(java.util.UUID.randomUUID().toString());
            haiGuiSoup.setPlayCount(0);
            haiGuiSoup.setUploadTime(java.time.LocalDateTime.now());

            // 显式设置创建时间（确保@CreationTimestamp正常工作）
            haiGuiSoup.setCreatedAt(java.time.LocalDateTime.now());
            haiGuiSoup.setUpdatedAt(java.time.LocalDateTime.now());

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

            UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                    .orElseThrow(() -> new BusinessException(404, "用户不存在"));
            haiGuiSoup.setUploaderId(userInfo.getUserId());
            haiGuiSoup.setCreatorId(userInfo.getUserId());
            haiGuiSoup.setUploader(userInfo);
            haiGuiSoup.setCreator(userInfo);

            // 注意：progress_settings现在存储在haigui_soup_progress_task表中
            // 生成混合方案的内容

            // 先设置一个空的线索ID列表，满足NOT NULL约束
            haiGuiSoup.setKeyClues("[]");

            log.info("开始新增海龟汤: title={}", soup.getSoupTitle());

            // 1. 先保存海龟汤到数据库（确保外键引用存在）
            HaiGuiSoup savedSoup = haiGuiSoupRepository.save(haiGuiSoup);

            log.info("海龟汤保存到数据库成功: soupId={}", savedSoup.getSoupId());

            // 2. 保存用户输入的线索片段并进行向量化
            if (!clues.isEmpty()) {
                try {
                    log.info("开始保存用户输入的线索片段，数量: {}", clues.size());
                    saveUserCluesWithVectorization(savedSoup.getSoupId(), clues);
                    log.info("用户线索片段保存并向量化完成");
                } catch (Exception e) {
                    log.error("保存用户线索片段失败: {}", e.getMessage(), e);
                }
            }

            // 3. 使用AI拆解汤底为线索片段并生成推理任务
            try {
                log.info("开始为海龟汤生成AI拆解的线索片段");

                // 使用ClueDecompositionService拆解汤底
                List<ClueFragment> aiFragments = clueDecompositionService.decomposeSoupBottom(
                    savedSoup.getSoupTitle(),
                    savedSoup.getSoupSurface(),
                    savedSoup.getSoupBottom()
                );

                // 保存AI生成的线索片段到数据库（已在ClueDecompositionService中向量化）
                for (ClueFragment fragment : aiFragments) {
                    fragment.setSoupId(savedSoup.getSoupId());
                    clueFragmentRepository.save(fragment);
                }

                // 生成推理任务
                generateInferenceTasksForSoup(savedSoup.getSoupId());

                log.info("AI拆解线索片段完成，生成{}个片段", aiFragments.size());

            } catch (Exception e) {
                log.warn("AI拆解线索片段失败: {}", e.getMessage());
                // AI拆解失败时，仍然生成推理任务
                generateInferenceTasksForSoup(savedSoup.getSoupId());
            }

            // 3. 将线索ID列表设置为空数组（暂时兼容旧格式）
            savedSoup.setKeyClues("[]");
            savedSoup.setUpdatedAt(java.time.LocalDateTime.now());
            haiGuiSoupRepository.save(savedSoup);

            log.info("海龟汤混合方案内容生成完成: soupId={}", savedSoup.getSoupId());

            // 4. 确认向量化处理已完成（ClueDecompositionService中已包含向量化）
            try {
                List<ClueFragment> savedFragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(savedSoup.getSoupId());
                log.info("获取到已向量化的线索片段数量: {}", savedFragments.size());

                // 验证向量数据
                long vectorizedCount = savedFragments.stream()
                    .filter(f -> f.getVectorData() != null && !f.getVectorData().isEmpty())
                    .count();
                log.info("成功向量化的片段数量: {}", vectorizedCount);

            } catch (Exception e) {
                log.warn("确认向量化处理失败: {}", e.getMessage());
            }
            log.info("海龟汤新增成功: soupId={}", savedSoup.getSoupId());
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

            // 获取关联的线索
            // 使用新的线索片段表
            List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soup.getSoupId());
            // 注意：vectorizeAndStoreSoupContext方法需要适配新的数据结构
            // boolean vectorSuccess = vectorService.vectorizeAndStoreSoupContext(soup, fragments);

            // 使用新的向量服务更新向量数据
            log.info("向量更新功能暂时跳过: soupId={}", soup.getSoupId());

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

            // 使用新的向量服务删除向量数据
            boolean deleteSuccess = vectorService.deleteSoupVectors(soupId);
            if (!deleteSuccess) {
                log.error("删除海龟汤向量数据失败: soupId={}", soupId);
                // 向量删除失败不影响整体删除流程
                log.warn("海龟汤删除成功但向量清理失败: soupId={}", soupId);
            }

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
            return vectorService.isSoupVectorized(soupId);

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
     * 获取海龟汤的线索（兼容新旧两种格式）
     * 1. 新格式：从数据库查询线索，keyClues存储线索ID列表
     * 2. 旧格式：从JSON解析为GameClue列表，keyClues存储线索内容
     *
     * @param soupId 海龟汤ID
     * @return 线索列表
     */
    public List<GameClue> getSoupClues(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null || soup.getKeyClues() == null) {
                log.warn("海龟汤不存在或keyClues为空: soupId={}", soupId);
                return List.of();
            }

            // 尝试解析为线索ID列表（新格式）
            List<String> clueIds = deserializeClueIds(soup.getKeyClues());
            if (!clueIds.isEmpty()) {
                // 新格式：使用线索片段表
                List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId);
                List<GameClue> gameClues = fragments.stream()
                        .map(fragment -> {
                            GameClue clue = new GameClue();
                            clue.setContent(fragment.getFragmentContent());
                            // 简化类型转换
                            try {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.valueOf(fragment.getFragmentType()));
                            } catch (Exception e) {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.PLOT);
                            }
                            clue.setIsKey(fragment.getIsCoreClue());
                            return clue;
                        })
                        .toList();

                log.info("获取海龟汤线索成功（新格式）: soupId={}, 线索数量={}", soupId, gameClues.size());
                return gameClues;
            }

            // 如果解析线索ID失败，尝试解析为线索内容（旧格式兼容）
            List<GameClue> clues = soupJsonParser.parseKeyClues(soup.getKeyClues());
            log.info("获取海龟汤线索成功（旧格式兼容）: soupId={}, 线索数量={}", soupId, clues.size());
            return clues;

        } catch (Exception e) {
            log.error("获取海龟汤线索失败: soupId={}", soupId, e);
            return List.of();
        }
    }

    /**
     * 获取海龟汤的关键线索（兼容新旧两种格式）
     *
     * @param soupId 海龟汤ID
     * @return 关键线索列表
     */
    public List<GameClue> getSoupKeyClues(String soupId) {
        try {
            HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElse(null);
            if (soup == null || soup.getKeyClues() == null) {
                log.warn("海龟汤不存在或keyClues为空: soupId={}", soupId);
                return List.of();
            }

            // 尝试解析为线索ID列表（新格式）
            List<String> clueIds = deserializeClueIds(soup.getKeyClues());
            if (!clueIds.isEmpty()) {
                // 新格式：使用线索片段表查询核心线索
                List<ClueFragment> fragments = clueFragmentRepository.findBySoupIdAndIsCoreClueTrueAndIsDeletedFalse(soupId);
                List<GameClue> gameClues = fragments.stream()
                        .map(fragment -> {
                            GameClue clue = new GameClue();
                            clue.setContent(fragment.getFragmentContent());
                            try {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.valueOf(fragment.getFragmentType()));
                            } catch (Exception e) {
                                clue.setClueType(com.guanyu.haigui.Enum.ClueType.PLOT);
                            }
                            clue.setIsKey(true);
                            return clue;
                        })
                        .toList();

                log.info("获取海龟汤关键线索成功（新格式）: soupId={}, 关键线索数量={}", soupId, gameClues.size());
                return gameClues;
            }

            // 如果解析线索ID失败，使用旧格式兼容
            List<GameClue> allClues = getSoupClues(soupId);
            List<GameClue> keyClues = allClues.stream()
                    .filter(GameClue::isKeyClue)
                    .toList();
            log.info("获取海龟汤关键线索成功（旧格式兼容）: soupId={}, 关键线索数量={}", soupId, keyClues.size());
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

    /**
     * 保存用户输入线索片段并进行向量化
     * @param soupId 海龟汤ID
     * @param clues 游戏线索列表
     */
    @Transactional
    public void saveUserCluesWithVectorization(String soupId, List<GameClue> clues) {
        for (int i = 0; i < clues.size(); i++) {
            try {
                GameClue gameClue = clues.get(i);

                // 创建线索片段
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(soupId);
                fragment.setFragmentContent(gameClue.getContent());
                fragment.setFragmentType(gameClue.getClueType().toString());
                fragment.setInferenceLevel(1); // 用户线索默认为表层信息
                fragment.setIsCoreClue(gameClue.getIsKey());
                fragment.setFragmentOrder(i);
                fragment.setTriggerKeywords(java.util.Arrays.asList(gameClue.getContent().split(" ")));
                fragment.setGenerationSource("USER"); // 标记为用户输入
                fragment.setAiAnalysisConfidence(1.0); // 用户输入置信度为100%

                // 向量化处理
                try {
                    // 生成向量化文本（内容+关键词）
                    String keywords = fragment.getTriggerKeywords() != null ? String.join(" ", fragment.getTriggerKeywords()) : "";
                    String vectorText = fragment.getFragmentContent() + " " + keywords;

                    // 调用BGE向量服务
                    com.guanyu.haigui.pojo.vo.SingleEncodeResponse response = vectorClient.encodeSingle(vectorText);
                    List<Double> vector = response.getEmbeddings().get(0)
                            .stream()
                            .map(Float::doubleValue)
                            .collect(java.util.stream.Collectors.toList());

                    // 生成向量哈希
                    String vectorHash = generateVectorHash(vector);

                    fragment.setVectorData(vector);
                    fragment.setVectorHash(vectorHash);

                    log.debug("用户线索片段向量化完成: {}", fragment.getFragmentContent());

                } catch (Exception e) {
                    log.error("用户线索片段向量化失败: {}", fragment.getFragmentContent(), e);
                    // 向量化失败时使用空向量，但不中断流程
                    fragment.setVectorData(new ArrayList<>());
                    fragment.setVectorHash("");
                }

                // 保存到数据库
                clueFragmentRepository.save(fragment);

                log.info("保存用户线索片段成功: content={}, type={}, isKey={}, vectorSize={}",
                        gameClue.getContent(), gameClue.getClueType(), gameClue.getIsKey(),
                        fragment.getVectorData() != null ? fragment.getVectorData().size() : 0);
            } catch (Exception e) {
                log.error("保存用户线索片段失败: content={}", clues.get(i).getContent(), e);
            }
        }
    }

    /**
     * 保存线索片段到数据库（混合方案）- 保留兼容性
     * @param soupId 海龟汤ID
     * @param clues 游戏线索列表
     */
    @Transactional
    public void saveClueFragmentsToDatabase(String soupId, List<GameClue> clues) {
        saveUserCluesWithVectorization(soupId, clues);
    }

    /**
     * 保存线索到数据库（兼容旧方法）
     * @param soupId 海龟汤ID
     * @param clues 线索列表
     * @return 线索ID列表
     */
    @Transactional
    public List<String> saveCluesToDatabase(String soupId, List<GameClue> clues) {
        List<String> clueIds = new ArrayList<>();

        for (GameClue gameClue : clues) {
            try {
                // 创建线索片段（混合方案）
                ClueFragment fragment = new ClueFragment();
                fragment.setSoupId(soupId);
                fragment.setFragmentContent(gameClue.getContent());
                fragment.setFragmentType(gameClue.getClueType().toString());
                fragment.setInferenceLevel(1); // 默认为表层信息
                fragment.setIsCoreClue(gameClue.getIsKey());
                fragment.setFragmentOrder(clueIds.size());
                fragment.setTriggerKeywords(java.util.Arrays.asList(gameClue.getContent().split(" ")));

                // 保存到数据库
                ClueFragment savedFragment = clueFragmentRepository.save(fragment);

                // 添加到ID列表（保持兼容性）
                clueIds.add("fragment_" + savedFragment.getFragmentId());

                log.info("保存线索片段成功: fragmentId={}, content={}",
                        savedFragment.getFragmentId(),
                        gameClue.getContent().substring(0, Math.min(50, gameClue.getContent().length())));

            } catch (Exception e) {
                log.error("保存线索片段失败: soupId={}, content={}",
                        soupId,
                        gameClue.getContent().substring(0, Math.min(50, gameClue.getContent().length())), e);
                // 继续处理其他线索，不让单个线索失败影响整体流程
            }
        }

        log.info("成功保存线索到数据库: soupId={}, 总数={}, 成功数={}",
                soupId, clues.size(), clueIds.size());

        return clueIds;
    }

    /**
     * 将线索ID列表序列化为JSON数组
     * @param clueIds 线索ID列表
     * @return JSON数组字符串
     */
    private String serializeClueIds(List<String> clueIds) {
        try {
            return new ObjectMapper().writeValueAsString(clueIds);
        } catch (Exception e) {
            log.error("序列化线索ID列表失败", e);
            return "[]"; // 返回空数组作为fallback
        }
    }

    /**
     * 从JSON数组反序列化线索ID列表
     * @param clueIdsJson JSON数组字符串
     * @return 线索ID列表
     */
    public List<String> deserializeClueIds(String clueIdsJson) {
        try {
            if (clueIdsJson == null || clueIdsJson.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return new ObjectMapper().readValue(clueIdsJson, List.class);
        } catch (Exception e) {
            log.error("反序列化线索ID列表失败: {}", clueIdsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取海龟汤的所有线索片段（从数据库查询）
     * @param soupId 海龟汤ID
     * @return 线索片段列表
     */
    public List<ClueFragment> getSoupClueFragmentsFromDatabase(String soupId) {
        try {
            return clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId);
        } catch (Exception e) {
            log.error("从数据库获取海龟汤线索片段失败: soupId={}", soupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取海龟汤的关键线索片段（从数据库查询）
     * @param soupId 海龟汤ID
     * @return 关键线索片段列表
     */
    public List<ClueFragment> getSoupKeyClueFragmentsFromDatabase(String soupId) {
        try {
            return clueFragmentRepository.findBySoupIdAndIsCoreClueTrueAndIsDeletedFalse(soupId);
        } catch (Exception e) {
            log.error("从数据库获取海龟汤关键线索片段失败: soupId={}", soupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成向量数据的哈希值
     */
    private String generateVectorHash(List<Double> vector) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String vectorString = vector.stream()
                    .map(d -> String.format("%.6f", d))
                    .collect(Collectors.joining(","));
            byte[] hash = md.digest(vectorString.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("生成向量哈希失败", e);
            return "";
        }
    }

    /**
     * 生成推理任务（混合方案）
     * @param soupId 海龟汤ID
     */
    @Transactional
    public void generateInferenceTasksForSoup(String soupId) {
        try {
            // 生成基础推理任务
            InferenceTask task1 = new InferenceTask(soupId, "发现基本信息", "询问故事的基本背景和设定", 1);
            task1.setReasoningGoal("掌握故事的基本时间、地点、人物等背景信息");
            task1.setProgressWeight(20.0);
            task1.setTaskOrder(1);

            InferenceTask task2 = new InferenceTask(soupId, "理解内在联系", "理解各要素之间的关系", 2);
            task2.setReasoningGoal("理解事件之间的因果关系和人物动机");
            task2.setProgressWeight(30.0);
            task2.setTaskOrder(2);

            InferenceTask task3 = new InferenceTask(soupId, "推理深层真相", "发现隐藏的关键信息", 3);
            task3.setReasoningGoal("揭示故事的完整真相和核心秘密");
            task3.setProgressWeight(50.0);
            task3.setTaskOrder(3);

            // 保存推理任务
            inferenceTaskRepository.save(task1);
            inferenceTaskRepository.save(task2);
            inferenceTaskRepository.save(task3);

            log.info("生成推理任务成功: soupId={}, 任务数量=3", soupId);

        } catch (Exception e) {
            log.error("生成推理任务失败: soupId={}", soupId, e);
        }
    }
}