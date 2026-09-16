package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.BackgroundService;
import com.bc.bcblog.vo.PageBackgroundVO;
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
    public Result<PageBackgroundVO> active(@RequestParam(defaultValue = "portal") String scope,
                                           @RequestParam(required = false) String page) {
        // page 是前台页面标识（home/photos/resources/sandbox），不传就用前台默认壁纸
        return Result.ok(backgroundService.resolve(scope, page));
    }
}
