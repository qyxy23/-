package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.dto.UploadHaiGuiSoupDTO;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.HaiGuiSoupAudit;
import com.guanyu.haigui.repository.HaiGuiSoupAuditRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.utils.MinioUtil;
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
    private final MinioUtil minioUtil;
    private final HaiGuiSoupAuditRepository haiGuiSoupAuditRepository;


    public String uploadHaiGuiSoupAvatar(MultipartFile avatarFile, String soupId) {
        String avatarUrl = minioUtil.generateAvatarUrl(avatarFile);
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId).orElseThrow
                (() -> new BusinessException(404, "故事不存在"));
        // -------------------------- 5. 更新图像到数据库 --------------------------

        // 若用户已有头像，先删除旧文件（避免占用空间）
        if (StringUtils.hasText(soup.getSoupAvatar())) {
            minioUtil.deleteAvatar(soup.getSoupAvatar());
            log.info("海龟汤图像删除成功 → 汤ID: {}, 旧URL: {}", soupId, soup.getSoupAvatar());
        }
        soup.setSoupAvatar(avatarUrl); // 存储访问URL（而非MinIO内部路径）
        haiGuiSoupRepository.save(soup);
        log.info("海龟汤头像更新成功 → 汤ID: {}, URL: {}", soup, avatarUrl);
        return avatarUrl;
    }

    public String uploadTurtleSoup(UploadHaiGuiSoupDTO soup) {
        HaiGuiSoupAudit audit = UploadHaiGuiSoupDTO.from(soup);
        haiGuiSoupAuditRepository.save(audit);
        return "上传成功,请等待审核";
    }
}