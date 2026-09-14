package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.SystemMonitorService;
import com.bc.bcblog.vo.SystemMonitorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台系统监控接口。 */
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    @GetMapping("/monitor")
    public Result<SystemMonitorVO> monitor() {
        return Result.ok(systemMonitorService.overview());
    }
}
