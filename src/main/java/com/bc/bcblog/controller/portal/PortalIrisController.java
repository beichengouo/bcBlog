package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.SiteAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 前台「IRIS 主页」接口：档案、当前状态、最近活动、她写的文章 */
@RestController
@RequestMapping("/api/portal/iris")
@RequiredArgsConstructor
public class PortalIrisController {

    private final SiteAiService siteAiService;

    @GetMapping
    public Result<Map<String, Object>> portal() {
        return Result.ok(siteAiService.portal());
    }
}
