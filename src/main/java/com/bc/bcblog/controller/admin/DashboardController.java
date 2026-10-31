package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.DashboardService;
import com.bc.bcblog.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台仪表盘接口。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    private final com.bc.bcblog.service.VisitGeoService visitGeoService;

    @GetMapping("/visitors")
    public Result<java.util.Map<String,Object>> visitors(@org.springframework.web.bind.annotation.RequestParam(defaultValue="7") int days) {
        return Result.ok(visitGeoService.stats(days));
    }

    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        return Result.ok(dashboardService.stats());
    }
}
