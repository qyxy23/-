package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haiqutang.app-update")
public class AppUpdateProperties {

    private PlatformConfig android = new PlatformConfig();

    @Data
    public static class PlatformConfig {

        /** 是否启用版本检查 */
        private boolean enabled = false;

        /** 最新 versionCode，须大于客户端当前值才会提示更新 */
        private int versionCode = 100;

        /** 展示用版本名 */
        private String versionName = "1.0.0";

        /** wgt | apk */
        private String updateType = "wgt";

        /** 热更新 wgt 或默认整包下载地址 */
        private String downloadUrl = "";

        /** 低于 min-supported-code 时使用的整包地址，为空则回退 download-url */
        private String apkDownloadUrl = "";

        /** 更新说明，支持多行 */
        private String changelog = "";

        /** 是否强制更新（不可稍后再说） */
        private boolean forceUpdate = false;

        /** 低于此 versionCode 时必须整包更新 */
        private int minSupportedCode = 0;

        /** 文件大小（字节），可选 */
        private Long fileSize;

        /** 文件 MD5，可选 */
        private String fileMd5 = "";
    }
}
