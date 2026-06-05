package com.guanyu.haigui.service.ServicesImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.Info.ClueFragmentInfo;
import com.guanyu.haigui.pojo.Info.InferenceTaskInfo;
import com.guanyu.haigui.pojo.dto.CreateTurtleSoupDTO;
import com.guanyu.haigui.pojo.dto.QueryTurtleSoupListDTO;
import com.guanyu.haigui.pojo.dto.UpdateHaiGuiAuditDTO;
import com.guanyu.haigui.pojo.dto.rejectTurtleSoupDTO;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.HaiGuiDetailResult;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.pojo.vo.QueryMyTurtleSoupListVO;
import com.guanyu.haigui.pojo.vo.QueryTurtleSoupListVO;
import com.guanyu.haigui.repository.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class AuditService {
    private final UserInfoRepository userInfoRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final SysRoleRepository sysRoleRepository;
    private final HaiGuiSoupInfoService haiGuiSoupInfoService;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final InferenceTaskRepository inferenceTaskRepository;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;


    /*
    添加审核员
     */
    public AddAuditUserVO addAuditUser(Long userId) {
        log.info("添加审核员：{}", userId);

        // 1. 验证当前管理员身份
        Long currentAdminId = BaseContext.getCurrentId();

        userInfoRepository.findById(currentAdminId)
                .orElseThrow(() -> new BusinessException(404, "当前用户不存在"));

        // 检查当前用户是否具有管理员角色
        boolean isAdmin = sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(currentAdminId, UserRoleEnum.ADMIN.getRoleId())
        );

        if (!isAdmin) {
            throw new BusinessException(403, "当前用户不是管理员，无权限执行此操作");
        }

        // 2. 验证目标用户存在
        UserInfo targetUser = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));


        // 4. 检查用户是否已有该角色
        boolean alreadyHasRole = sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(targetUser.getUserId(), UserRoleEnum.SOUP_AUDITOR.getRoleId())
        );

        if (alreadyHasRole) {
            return AddAuditUserVO.error("该用户已是审核员");
        }

        // 1. 获取审核员角色ID（直接从枚举中获取）
        Long auditorRoleId = UserRoleEnum.SOUP_AUDITOR.getRoleId();

        SysRole role = sysRoleRepository.findById(auditorRoleId)
                .orElseThrow(() -> new BusinessException(404, "角色不存在"));

        // 2. 创建用户-角色关联对象
        SysUserRole.UserRoleId id = new SysUserRole.UserRoleId(userId, auditorRoleId);
        SysUserRole userRole = new SysUserRole();
        userRole.setId(id);
        userRole.setRole(role);
        userRole.setUser(targetUser);

        // 3. 直接保存关联
        try {
            sysUserRoleRepository.save(userRole);
            return AddAuditUserVO.success();
        } catch (DataIntegrityViolationException e) {
            // 捕获唯一约束冲突异常（用户已有该角色）
            return AddAuditUserVO.error("该用户已是审核员");
        }
    }

    public HaiGuiInfoResult generateInfo(Long auditId) {
        HaiGuiSoupAudit audit = findById(auditId);
        // 生成提示
        String prompt = haiGuiSoupInfoService.generatePrompt(audit);
        // 调用AI生成信息
        HaiGuiInfoResult result = haiGuiSoupInfoService.generateInfo(prompt);
        ToJson(audit, result.getManual(), result.getFragments(), result.getInferenceTasks());
        haiGuiSoupAuditRepository.save(audit);
        return result;
    }

    public String createTurtleSoup(CreateTurtleSoupDTO dto) {
        RLock lock = redissonClient.getLock("turtle_soup_create_lock");
        try {
            // 尝试立即获取锁（不等待）
            if (!lock.tryLock()) {
                throw new BusinessException(429, "请稍等，当前有人正在操作");
            }

            try {
                UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                        .orElseThrow(() -> new BusinessException(404, "用户不存在"));
                if (!hasAuditPermission(userInfo.getUserId())) {
                    throw new BusinessException(403, "您不是审核员,无权限");
                }
                HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(dto.getAuditRecordId())
                        .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
                HaiGuiSoup soup = dto.fromToHaiGuiSoup(userInfo);
                haiGuiSoupRepository.save(soup);
                System.out.println("soup = " + soup);
                audit.setOriginalSoupId(soup.getSoupId());
                audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.APPROVED);
                audit.setAuditorId(BaseContext.getCurrentId());
                audit.setAuditTime(LocalDateTime.now());
                System.out.println("audit = " + dto.getFragments());
                ToJson(audit,dto.getManual(),dto.getFragments(),dto.getInferenceTasks());
                System.out.println("audit = " + audit.getDraftFragments());
                haiGuiSoupAuditRepository.save(audit);
                // 向量化线索并进行存储
                Map<Integer, Long> fragments = haiGuiSoupInfoService.convertToClueFragmentsAndSave(dto.getFragments(), soup);
                List<InferenceTask> tasks = haiGuiSoupInfoService.convertToInferenceTasks(dto.getInferenceTasks(), soup, fragments);
                if (fragments.isEmpty() || tasks.isEmpty()) {
                    throw new BusinessException(500, "请检查线索和推理任务是否填写正确");
                }
                return "创建成功";
            } finally {
                // 安全解锁：检查当前线程是否持有锁
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (BusinessException e) {
            if (e.getMessage().contains("当前有人正在操作")) {
                // 提示用户稍后重试
                return "error: 请稍后重试";
            }
            throw e;
        }
    }

    public QueryTurtleSoupListVO queryTurtleSoupList(QueryTurtleSoupListDTO dto) {
        // 校验并修正页码参数
        int pageNum = dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;

        // 创建分页请求
        Pageable pageable = PageRequest.of(
                pageNum - 1, // Spring Data页码从0开始
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt") // 按创建时间倒序
        );

        // 执行分页查询
        Page<HaiGuiSoupAudit> pageResult;
        String title = dto.getSoupTitle();
        String statusStr = dto.getAuditStatus();

        try {
            if (title != null && !title.isEmpty() && statusStr != null && !statusStr.isEmpty()) {
                // 标题和状态都有值
                HaiGuiSoupAudit.AuditStatus status = HaiGuiSoupAudit.AuditStatus.valueOf(statusStr);
                pageResult = haiGuiSoupAuditRepository.findByTitleContainingAndAuditStatus(
                        title, status, pageable);

            } else if (title != null && !title.isEmpty()) {
                // 只有标题
                pageResult = haiGuiSoupAuditRepository.findByTitleContaining(title, pageable);

            } else if (statusStr != null && !statusStr.isEmpty()) {
                // 只有状态
                HaiGuiSoupAudit.AuditStatus status = HaiGuiSoupAudit.AuditStatus.valueOf(statusStr);
                pageResult = haiGuiSoupAuditRepository.findByAuditStatus(status, pageable);

            } else {
                // 无过滤条件 - 查询所有
                pageResult = haiGuiSoupAuditRepository.findAll(pageable);
            }
        } catch (IllegalArgumentException e) {
            // 状态枚举转换失败时使用无条件查询
            pageResult = haiGuiSoupAuditRepository.findAll(pageable);
        }

        // 转换为VO
        return convertToVO(pageResult);
    }

    private QueryTurtleSoupListVO convertToVO(Page<HaiGuiSoupAudit> page) {
        QueryTurtleSoupListVO vo = new QueryTurtleSoupListVO();
        vo.setTotal((int) page.getTotalElements());
        vo.setPages(page.getTotalPages());

        List<QueryTurtleSoupListVO.TurtleSoupItem> items = page.getContent().stream()
                .map(this::convertToItem)
                .collect(Collectors.toList());

        vo.setList(items);
        return vo;
    }

    private QueryTurtleSoupListVO.TurtleSoupItem convertToItem(HaiGuiSoupAudit audit) {
        QueryTurtleSoupListVO.TurtleSoupItem item = new QueryTurtleSoupListVO.TurtleSoupItem();
        item.setAuditId(audit.getAuditId());
        item.setSoupTitle(audit.getTitle());
        item.setSoupSurface(audit.getSurface());
        item.setAuditStatus(audit.getAuditStatus().name());
        item.setCreateTime(audit.getCreatedAt() != null ?
                audit.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        return item;
    }

    public HaiGuiDetailResult queryTurtleSoupDetail(Long auditId) {
        HaiGuiSoupAudit audit = requireAuditAccess(auditId);
        HaiGuiInfoResult haiGuiInfoResult = haiGuiSoupInfoService.getFragmentsAndTasks(
                audit.getDraftFragments(), audit.getDraftTasks());

        if (shouldLoadPublishedExtras(audit, haiGuiInfoResult)) {
            haiGuiInfoResult = loadPublishedExtras(audit.getOriginalSoupId(), haiGuiInfoResult);
        }

        return HaiGuiDetailResult.fromHaiGuiSoupAudit(audit, haiGuiInfoResult);
    }

    private boolean shouldLoadPublishedExtras(HaiGuiSoupAudit audit, HaiGuiInfoResult draftInfo) {
        if (!StringUtils.hasText(audit.getOriginalSoupId())) {
            return false;
        }
        boolean fragmentsEmpty = draftInfo.getFragments() == null
                || draftInfo.getFragments().isEmpty()
                || draftInfo.getFragments().stream().noneMatch(f -> StringUtils.hasText(f.getContent()));
        boolean tasksEmpty = draftInfo.getInferenceTasks() == null || draftInfo.getInferenceTasks().isEmpty();
        return fragmentsEmpty || tasksEmpty;
    }

    private HaiGuiInfoResult loadPublishedExtras(String soupId, HaiGuiInfoResult draftInfo) {
        List<ClueFragmentInfo> fragments = draftInfo.getFragments();
        List<InferenceTaskInfo> tasks = draftInfo.getInferenceTasks();

        if (fragments == null || fragments.isEmpty()
                || fragments.stream().noneMatch(f -> StringUtils.hasText(f.getContent()))) {
            fragments = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId).stream()
                    .map(fragment -> {
                        ClueFragmentInfo info = new ClueFragmentInfo();
                        info.setContent(fragment.getFragmentContent());
                        info.setTriggerKeywords(fragment.getTriggerKeywords());
                        return info;
                    })
                    .collect(Collectors.toList());
        }

        if (tasks == null || tasks.isEmpty()) {
            tasks = inferenceTaskRepository.findBySoupIdAndIsDeletedFalseOrderByTaskOrderAsc(soupId).stream()
                    .map(task -> {
                        InferenceTaskInfo info = new InferenceTaskInfo();
                        info.setTaskName(task.getTaskName());
                        info.setTaskDescription(task.getTaskDescription());
                        info.setTargetKeywords(task.getTargetKeywords());
                        info.setReasoningGoal(task.getReasoningGoal());
                        info.setProgressWeight(task.getProgressWeight() != null
                                ? task.getProgressWeight().doubleValue() : 0.0);
                        info.setTaskOrder(task.getTaskOrder());
                        info.setPrerequisiteFragmentIds(task.getPrerequisiteFragmentIds());
                        return info;
                    })
                    .collect(Collectors.toList());
        }

        return new HaiGuiInfoResult(draftInfo.getManual(), fragments, tasks);
    }

    /**
     * 上传者修改自己的海龟汤（待审核 / 已拒绝可改，已通过只读）
     */
    public String updateMyTurtleSoup(UpdateHaiGuiAuditDTO dto) {
        Long userId = BaseContext.getCurrentId();
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(dto.getAuditId())
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        if (!audit.getUploaderId().equals(userId)) {
            throw new BusinessException(403, "无权修改此海龟汤");
        }
        if (audit.getAuditStatus() == HaiGuiSoupAudit.AuditStatus.APPROVED) {
            throw new BusinessException(403, "已通过审核，无法修改");
        }
        if (audit.getAuditStatus() != HaiGuiSoupAudit.AuditStatus.PENDING
                && audit.getAuditStatus() != HaiGuiSoupAudit.AuditStatus.REJECTED) {
            throw new BusinessException(403, "当前状态无法修改");
        }

        boolean wasRejected = audit.getAuditStatus() == HaiGuiSoupAudit.AuditStatus.REJECTED;
        audit.setTitle(dto.getSoupTitle());
        audit.setSurface(dto.getSoupSurface());
        audit.setBottom(dto.getSoupBottom());
        audit.setDefaultMaxQuestions(dto.getDefaultMaxQuestions());
        audit.setEstimatedDuration(dto.getEstimatedDuration());
        audit.setPlayerCount(dto.getPlayerCount());
        audit.setDifficultyLevel(dto.getDifficultyLevel());
        audit.setTags(dto.getTag());
        if (wasRejected) {
            audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.PENDING);
            audit.setAuditComment(null);
            audit.setAuditTime(null);
            audit.setAuditorId(null);
        }
        haiGuiSoupAuditRepository.save(audit);
        return wasRejected ? "已重新提交审核" : "修改成功";
    }

    private HaiGuiSoupAudit requireAuditAccess(Long auditId) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        Long userId = BaseContext.getCurrentId();
        if (audit.getUploaderId().equals(userId) || hasAuditPermission(userId)) {
            return audit;
        }
        throw new BusinessException(403, "无权查看此海龟汤");
    }

    public String rejectTurtleSoup(rejectTurtleSoupDTO rejectTurtleSoupDTO) {
        HaiGuiSoupAudit audit = findById(rejectTurtleSoupDTO.getAuditId());
        audit.setAuditorId(BaseContext.getCurrentId());
        audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.REJECTED);
        if (rejectTurtleSoupDTO.getReason() != null) {
            audit.setAuditComment(rejectTurtleSoupDTO.getReason());
        } else {
            audit.setAuditComment("审核未通过");
        }
        audit.setAuditTime(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
        return "拒绝已成功";
    }

    public QueryMyTurtleSoupListVO queryMyTurtleSoupList(QueryTurtleSoupListDTO dto) {
        // 获取当前用户ID
        Long uploaderId = BaseContext.getCurrentId();
        if (uploaderId == null) {
            throw new RuntimeException("用户未登录");
        }

        // 校验并修正页码参数
        int pageNum = (dto.getPageNum() == null || dto.getPageNum() <= 0) ? 1 : dto.getPageNum();
        int pageSize = (dto.getPageSize() == null || dto.getPageSize() <= 0) ? 10 : dto.getPageSize();

        // 创建分页请求
        Pageable pageable = PageRequest.of(
                pageNum - 1, // Spring Data页码从0开始
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt") // 按创建时间倒序
        );

        // 执行分页查询
        Page<HaiGuiSoupAudit> pageResult;
        String title = dto.getSoupTitle();
        String statusStr = dto.getAuditStatus(); // 确保DTO中有此字段

        try {
            if (StringUtils.hasText(title) && StringUtils.hasText(statusStr)) {
                // 标题和状态都有值
                HaiGuiSoupAudit.AuditStatus status = HaiGuiSoupAudit.AuditStatus.valueOf(statusStr);
                pageResult = haiGuiSoupAuditRepository.findByUploaderIdAndTitleContainingAndAuditStatus(
                        uploaderId, title, status, pageable);

            } else if (StringUtils.hasText(title)) {
                // 只有标题
                pageResult = haiGuiSoupAuditRepository.findByUploaderIdAndTitleContaining(
                        uploaderId, title, pageable);

            } else if (StringUtils.hasText(statusStr)) {
                // 只有状态
                HaiGuiSoupAudit.AuditStatus status = HaiGuiSoupAudit.AuditStatus.valueOf(statusStr);
                pageResult = haiGuiSoupAuditRepository.findByUploaderIdAndAuditStatus(
                        uploaderId, status, pageable);

            } else {
                // 无过滤条件
                pageResult = haiGuiSoupAuditRepository.findByUploaderId(uploaderId, pageable);
            }
        } catch (IllegalArgumentException e) {
            // 状态枚举转换失败时使用无条件查询
            pageResult = haiGuiSoupAuditRepository.findByUploaderId(uploaderId, pageable);
        }

        // 转换为VO
        return convertToMyVO(pageResult);
    }

    private QueryMyTurtleSoupListVO convertToMyVO(Page<HaiGuiSoupAudit> pageResult) {
        QueryMyTurtleSoupListVO vo = new QueryMyTurtleSoupListVO();
        vo.setTotal((int) pageResult.getTotalElements());
        vo.setPages(pageResult.getTotalPages());

        List<QueryMyTurtleSoupListVO.TurtleSoupItem> items = pageResult.getContent().stream()
                .map(this::convertToMyItem)
                .collect(Collectors.toList());

        vo.setList(items);
        return vo;
    }

    private QueryMyTurtleSoupListVO.TurtleSoupItem convertToMyItem(HaiGuiSoupAudit audit) {
        QueryMyTurtleSoupListVO.TurtleSoupItem item = new QueryMyTurtleSoupListVO.TurtleSoupItem();

        item.setAuditId(audit.getAuditId());
        item.setSoupTitle(audit.getTitle());
        item.setSoupSurface(audit.getSurface());
        item.setSoupBottom(audit.getBottom());

        // 修复标签显示问题
        item.setSoupTags(audit.getTags() != null ? audit.getTags().getDescription() : "");

        // 修复字段映射错误
        item.setSoupEstimatedQuestions(audit.getDefaultMaxQuestions()); // 题目数量
        item.setSoupDifficultyLevel(audit.getDifficultyLevel().name()); // 难度
        item.setSoupPlayerCount(String.valueOf(audit.getPlayerCount())); // 玩家数量
        item.setSoupEstimatedDuration(String.valueOf(audit.getEstimatedDuration())); // 时长

        item.setAuditStatus(audit.getAuditStatus().name());
        item.setRejectReason(audit.getAuditComment());
        item.setCreateTime(audit.getCreatedAt() != null ?
                audit.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");

        return item;
    }


    public String uploadHaiGuiAudit(UpdateHaiGuiAuditDTO dto) {
        // 1. 获取审核记录
        HaiGuiSoupAudit audit = findById(dto.getAuditId());

        updateAuditRecord(dto, audit);

        return "修改成功,请继续审核";
    }

    private void updateAuditRecord(UpdateHaiGuiAuditDTO dto, HaiGuiSoupAudit audit) {
        // 2. 检查状态
        if (audit.getAuditStatus() == HaiGuiSoupAudit.AuditStatus.REJECTED) {
            throw new BusinessException(403, "已拒绝此汤,无法进行修改");
        }

        // 3. 更新基本字段
        audit.setTitle(dto.getSoupTitle());
        audit.setSurface(dto.getSoupSurface());
        audit.setBottom(dto.getSoupBottom());
        audit.setDefaultMaxQuestions(dto.getDefaultMaxQuestions());
        audit.setEstimatedDuration(dto.getEstimatedDuration());
        audit.setPlayerCount(dto.getPlayerCount());
        audit.setDifficultyLevel(dto.getDifficultyLevel());
        audit.setTags(dto.getTag());
        audit.setUploaderId(BaseContext.getCurrentId());
        audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.PENDING);
        audit.setAuditorId(BaseContext.getCurrentId());

        ToJson(audit, dto.getDraftManual(), dto.getDraftFragments(), dto.getDraftTasks());

        // 8. 保存更新
        haiGuiSoupAuditRepository.save(audit);
    }



    private void ToJson(HaiGuiSoupAudit audit, String manual, List<ClueFragmentInfo> clue, List<InferenceTaskInfo> task) {
        try {
            // 4. 序列化主持人手册（包装为 JSON 对象）
            ObjectNode manualNode = objectMapper.createObjectNode();
            manualNode.put("content", manual != null ? manual : "");
            audit.setDraftManual(objectMapper.writeValueAsString(manualNode));

            // 5. 序列化线索片段列表并转为 JsonNode
            String fragmentsStr = objectMapper.writeValueAsString(clue);
            audit.setDraftFragments(objectMapper.readTree(fragmentsStr));

            // 6. 序列化推理任务列表并转为 JsonNode
            String tasksStr = objectMapper.writeValueAsString(task);
            audit.setDraftTasks(objectMapper.readTree(tasksStr));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "JSON序列化失败: " + e.getMessage());
        }
    }


    private boolean hasAuditPermission(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
    }

    private HaiGuiSoupAudit findById(Long auditId) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!hasAuditPermission(userInfo.getUserId())) {
            throw new BusinessException(403, "您不是审核员,无权限");
        }
        return audit;
    }
}
