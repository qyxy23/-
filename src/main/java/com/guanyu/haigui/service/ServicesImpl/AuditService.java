package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.CreateTurtleSoupDTO;
import com.guanyu.haigui.pojo.dto.HaiGuiInfoGenerateDTO;
import com.guanyu.haigui.pojo.dto.QueryTurtleSoupListDTO;
import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.HaiGuiDetailResult;
import com.guanyu.haigui.pojo.result.HaiGuiInfoResult;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.pojo.vo.QueryTurtleSoupListVO;
import com.guanyu.haigui.repository.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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


    /*
    添加审核员
     */
    public AddAuditUserVO addAuditUser(Long userId) {
        log.info("添加审核员：{}", userId);

        // 1. 验证当前管理员身份
        Long currentAdminId = BaseContext.getCurrentId();

        userInfoRepository.findById(currentAdminId)
                .orElseThrow(() -> new BusinessException(404,"管理员不存在"));
        
        // 检查当前用户是否具有管理员角色
        boolean isAdmin = sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(currentAdminId, UserRoleEnum.ADMIN.getRoleId())
        );
        
        if (!isAdmin) {
            throw new BusinessException(403, "当前用户不是管理员，无权限执行此操作");
        }

        // 2. 验证目标用户存在
        UserInfo targetUser = userInfoRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404,"用户不存在"));


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

    public HaiGuiInfoResult generateInfo(HaiGuiInfoGenerateDTO titleGenerateDTO) {
        UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        boolean userRole = sysUserRoleRepository.existsById(new SysUserRole.UserRoleId(userInfo.getUserId(), UserRoleEnum.SOUP_AUDITOR.getRoleId()));
        if (!userRole) {
            throw new BusinessException(403, "您不是审核员,无权限");
        }
        // 生成提示
        String prompt = haiGuiSoupInfoService.generatePrompt(titleGenerateDTO);
        // 调用AI生成信息
        return haiGuiSoupInfoService.generateInfo(prompt);
    }

    public String createTurtleSoup(CreateTurtleSoupDTO createTurtleSoupDTO) {
        UserInfo userInfo = userInfoRepository.findById(BaseContext.getCurrentId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        boolean userRole = sysUserRoleRepository.existsById(new SysUserRole.UserRoleId(userInfo.getUserId(), UserRoleEnum.SOUP_AUDITOR.getRoleId()));
        if (!userRole) {
            throw new BusinessException(403, "您不是审核员,无权限");
        }
        HaiGuiSoup soup = createTurtleSoupDTO.fromToHaiGuiSoup(userInfo);
        haiGuiSoupRepository.save(soup);
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(createTurtleSoupDTO.getAuditRecordId())
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        audit.setOriginalSoupId(soup.getSoupId());
        audit.setAuditStatus(HaiGuiSoupAudit.AuditStatus.APPROVED);
        audit.setAuditorId(BaseContext.getCurrentId());
        audit.setAuditTime(LocalDateTime.now());
        haiGuiSoupAuditRepository.save(audit);
        //向量化线索并进行存储
        Map<Integer, Long> fragments = haiGuiSoupInfoService.convertToClueFragmentsAndSave(createTurtleSoupDTO.getFragments(),soup);
        List<InferenceTask> tasks = haiGuiSoupInfoService.convertToInferenceTasks(createTurtleSoupDTO,soup,fragments);
        if(fragments.isEmpty()||tasks.isEmpty()){
            throw new BusinessException(500,"请检查线索和推理任务是否填写正确");
        }
        return "创建成功";
    }

    public QueryTurtleSoupListVO queryTurtleSoupList(QueryTurtleSoupListDTO dto) {
        // 获取当前用户ID
        Long uploaderId = BaseContext.getCurrentId();

        // 校验并修正页码参数
        int pageNum = dto.getPageNum() != null && dto.getPageNum() > 0 ? dto.getPageNum() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 10;


        // 创建分页请求
        // 创建分页请求
        Pageable pageable = PageRequest.of(
                pageNum - 1, // Spring Data页码从0开始
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt") // 按创建时间倒序
        );

        // 执行分页查询
        Page<HaiGuiSoupAudit> pageResult;
        if (dto.getSoupTitle() != null && !dto.getSoupTitle().isEmpty()) {
            // 带标题搜索的查询
            pageResult = haiGuiSoupAuditRepository.findByUploaderIdAndTitleContainingAndAuditStatus(
                    uploaderId,
                    dto.getSoupTitle(),
                    dto.getAuditStatus() != null ?
                            HaiGuiSoupAudit.AuditStatus.valueOf(dto.getAuditStatus()) : null,
                    pageable
            );
        } else {
            // 普通分页查询
            pageResult = haiGuiSoupAuditRepository.findByUploaderId(uploaderId, pageable);
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
                .toList();

        vo.setList(items);
        return vo;
    }

    private QueryTurtleSoupListVO.TurtleSoupItem convertToItem(HaiGuiSoupAudit audit) {
        QueryTurtleSoupListVO.TurtleSoupItem item = new QueryTurtleSoupListVO.TurtleSoupItem();
        item.setAuditId(audit.getAuditId());
        item.setSoupTitle(audit.getTitle());
        item.setSoupSurface(audit.getSurface());
        item.setAuditStatus(audit.getAuditStatus().name());
        item.setCreateTime(audit.getCreatedAt().toString());
        return item;
    }

    public HaiGuiDetailResult queryTurtleSoupDetail(Long auditId) {
        HaiGuiSoupAudit audit = haiGuiSoupAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(404, "审核记录不存在"));
        return HaiGuiDetailResult.fromHaiGuiSoupAudit(audit);
    }
}
