package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.vo.AppVersionVO;
import com.guanyu.haigui.result.Result;
import com.guanyu.haigui.service.AppUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Tag(name = "App 版本", description = "客户端版本检查与热更新")
public class AppUpdateController {

    private final AppUpdateService appUpdateService;

    @Operation(summary = "检查 App 是否有新版本")
    @GetMapping("/version")
    public Result<AppVersionVO> checkVersion(
            @RequestParam(defaultValue = "android") String platform,
            @RequestParam int versionCode) {
        return Result.success(appUpdateService.checkVersion(platform, versionCode));
    }
}
