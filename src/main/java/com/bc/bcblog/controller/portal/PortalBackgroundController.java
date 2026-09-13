package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.Background;
import com.bc.bcblog.service.BackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 前台背景接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/background")
@RequiredArgsConstructor
public class PortalBackgroundController {

    private final BackgroundService backgroundService;

    @GetMapping
    public Result<Background> active(@RequestParam(defaultValue = "portal") String scope) {
        return Result.ok(backgroundService.activeByScope(scope));
    }
}
