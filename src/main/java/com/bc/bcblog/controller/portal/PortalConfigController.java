package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台站点设置接口（游客可访问，用于站点名称等展示）。
 */
@RestController
@RequestMapping("/api/portal/config")
@RequiredArgsConstructor
public class PortalConfigController {

    private final ConfigService configService;

    @GetMapping
    public Result<SiteConfigVO> get() {
        return Result.ok(configService.get());
    }
}
