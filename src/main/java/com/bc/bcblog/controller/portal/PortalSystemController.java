package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.SystemInfoService;
import com.bc.bcblog.vo.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前台系统信息接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/system")
@RequiredArgsConstructor
public class PortalSystemController {

    private final SystemInfoService systemInfoService;

    @GetMapping("/info")
    public Result<SystemInfoVO> info() {
        return Result.ok(systemInfoService.getSystemInfo());
    }
}
