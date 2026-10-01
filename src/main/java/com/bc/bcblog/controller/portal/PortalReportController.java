package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.ReportDTO;
import com.bc.bcblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前台举报接口（违法有害信息举报入口，需登录） */
@RestController
@RequestMapping("/api/portal/report")
@RequiredArgsConstructor
public class PortalReportController {

    private final ReportService reportService;

    @PostMapping
    public Result<Void> submit(@RequestBody ReportDTO dto) {
        reportService.submit(dto);
        return Result.ok();
    }
}
