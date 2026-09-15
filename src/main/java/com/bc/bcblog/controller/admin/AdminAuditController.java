package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.AdminApiLog;
import com.bc.bcblog.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 调用审计（仅超级管理员，权限在 WebConfig 统一拦截）。 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogService auditLogService;

    @GetMapping("/list")
    public Result<PageResult<AdminApiLog>> list(@RequestParam(required = false) Long adminId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return Result.ok(auditLogService.page(adminId, keyword, startDate, endDate, page, size));
    }

    /** 按管理员汇总今天的调用次数与近 7 天次数 */
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary() {
        return Result.ok(auditLogService.summary());
    }
}
