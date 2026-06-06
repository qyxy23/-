package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.UserRoleEnum;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.context.BaseContext;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.pojo.model.SysUserRole;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.SysUserRoleRepository;
import com.guanyu.haigui.utils.CosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 海龟汤服务实现类（重构版）
 * 集成向量化功能，提供智能搜索和推荐
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TurtleSoupService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final CosUtil cosUtil;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;
    private final SysUserRoleRepository sysUserRoleRepository;


    public String uploadHaiGuiSoupAvatar(MultipartFile avatarFile, String soupId) {
        Long userId = BaseContext.getCurrentId();
        if (!hasAuditPermission(userId)) {
            throw new BusinessException(403, "仅审核员可上传海龟汤封面");
        }
        String avatarUrl = cosUtil.uploadImage(avatarFile);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElseThrow
                (() -> new BusinessException(404, "故事不存在"));

        if (StringUtils.hasText(soup.getSoupAvatar())) {
            cosUtil.deleteByUrl(soup.getSoupAvatar());
            log.info("海龟汤图像删除成功 → 汤ID: {}, 旧URL: {}", soupId, soup.getSoupAvatar());
        }
        soup.setSoupAvatar(avatarUrl);
        haiGuiSoupRepository.save(soup);
        log.info("海龟汤头像更新成功 → 汤ID: {}, URL: {}", soup, avatarUrl);
        return avatarUrl;
    }

    public String uploadTurtleSoup(UploadHaiGuiSoupDTO soup) {
        HaiGuiSoupAudit audit = UploadHaiGuiSoupDTO.from(soup);
        haiGuiSoupAuditRepository.save(audit);
        return "上传成功,请等待审核";
    }

    private boolean hasAuditPermission(Long userId) {
        return sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.SOUP_AUDITOR.getRoleId()))
                || sysUserRoleRepository.existsById(
                new SysUserRole.UserRoleId(userId, UserRoleEnum.ADMIN.getRoleId()));
    }
}