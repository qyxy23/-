package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.vo.AddAuditUserVO;
import com.guanyu.haigui.service.ServicesImpl.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "审核接口", description = "审核相关接口")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @Operation(summary = "管理员添加审核用户")
    @PostMapping("/addAuditUser/{userId}")
    public AddAuditUserVO addAuditUser(@PathVariable Long userId) {
        return auditService.addAuditUser(userId);
    }
}
