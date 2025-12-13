package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class AuditService {
    private final UserInfoRepository userInfoRepository;
    private final SysUserRoleRepository sysUserRoleRepository;


    /*
    添加审核员
     */
    public AddAuditUserVO addAuditUser(Long userId) {
        log.info("添加审核员：{}", userId);

        // 1. 验证当前管理员身份
        Long currentAdminId = BaseContext.getCurrentId();

        UserInfo admin = userInfoRepository.findById(currentAdminId)
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

        // 2. 创建用户-角色关联对象
        SysUserRole.UserRoleId id = new SysUserRole.UserRoleId(userId, auditorRoleId);
        SysUserRole userRole = new SysUserRole();
        userRole.setId(id);

        // 3. 直接保存关联
        try {
            sysUserRoleRepository.save(userRole);
            return AddAuditUserVO.success();
        } catch (DataIntegrityViolationException e) {
            // 捕获唯一约束冲突异常（用户已有该角色）
            return AddAuditUserVO.error("该用户已是审核员");
        }
    }
}
