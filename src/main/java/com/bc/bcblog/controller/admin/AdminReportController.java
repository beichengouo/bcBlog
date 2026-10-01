package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.ReportHandleDTO;
import com.bc.bcblog.entity.SysReport;
import com.bc.bcblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台举报处理接口 */
@RestController
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping("/page")
    public Result<PageResult<SysReport>> page(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(reportService.page(status, page, size));
    }

    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody ReportHandleDTO dto) {
        reportService.handle(id, dto);
        return Result.ok();
    }
}
