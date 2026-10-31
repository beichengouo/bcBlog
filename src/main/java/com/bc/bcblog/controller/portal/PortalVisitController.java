package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前台访问量上报接口。 */
@RestController
@RequestMapping("/api/portal/visit")
@RequiredArgsConstructor
public class PortalVisitController {

    private final VisitService visitService;

    private final com.bc.bcblog.service.VisitGeoService visitGeoService;

    @PostMapping("/report")
    public Result<Void> report(javax.servlet.http.HttpServletRequest request) {
        visitService.report();
        visitGeoService.record(com.bc.bcblog.service.VisitorGeoService.clientIp(request));
        return Result.ok();
    }
}
