package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台 IP 定位接口，供登录日志手动查询。 */
@RestController
@RequestMapping("/api/admin/ip-location")
@RequiredArgsConstructor
public class AdminIpLocationController {

    private final IpLocationService ipLocationService;

    @GetMapping("/query")
    public Result<String> query(@RequestParam(required = false) String ip) {
        return Result.ok(ipLocationService.query(ip));
    }
}
