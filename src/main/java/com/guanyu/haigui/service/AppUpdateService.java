package com.guanyu.haigui.service;

import com.guanyu.haigui.config.AppUpdateProperties;
import com.guanyu.haigui.pojo.vo.AppVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AppUpdateService {

    private final AppUpdateProperties appUpdateProperties;

    public AppVersionVO checkVersion(String platform, int clientVersionCode) {
        AppVersionVO vo = new AppVersionVO();
        vo.setNeedUpdate(false);

        if (!"android".equalsIgnoreCase(platform)) {
            return vo;
        }

        AppUpdateProperties.PlatformConfig config = appUpdateProperties.getAndroid();
        if (config == null || !config.isEnabled()) {
            return vo;
        }

        int latestCode = config.getVersionCode();
        if (clientVersionCode >= latestCode) {
            return vo;
        }

        if (!StringUtils.hasText(config.getDownloadUrl())
                && !StringUtils.hasText(config.getApkDownloadUrl())) {
            return vo;
        }

        vo.setNeedUpdate(true);
        vo.setVersionCode(latestCode);
        vo.setVersionName(config.getVersionName());
        vo.setChangelog(config.getChangelog());
        vo.setFileSize(config.getFileSize());
        vo.setFileMd5(config.getFileMd5());

        boolean belowMinSupported = config.getMinSupportedCode() > 0
                && clientVersionCode < config.getMinSupportedCode();

        if (belowMinSupported) {
            vo.setForceUpdate(true);
            vo.setUpdateType("apk");
            vo.setDownloadUrl(resolveApkDownloadUrl(config));
            return vo;
        }

        vo.setForceUpdate(config.isForceUpdate());
        vo.setUpdateType(normalizeUpdateType(config.getUpdateType()));
        vo.setDownloadUrl(config.getDownloadUrl());
        return vo;
    }

    private static String resolveApkDownloadUrl(AppUpdateProperties.PlatformConfig config) {
        if (StringUtils.hasText(config.getApkDownloadUrl())) {
            return config.getApkDownloadUrl();
        }
        return config.getDownloadUrl();
    }

    private static String normalizeUpdateType(String updateType) {
        return "apk".equalsIgnoreCase(updateType) ? "apk" : "wgt";
    }
}
