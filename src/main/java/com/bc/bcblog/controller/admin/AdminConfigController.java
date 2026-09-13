package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台站点设置接口。
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final ConfigService configService;

    @GetMapping
    public Result<SiteConfigVO> get() {
        return Result.ok(configService.get());
    }

    @PutMapping
    public Result<Void> save(@RequestBody SiteConfigVO vo) {
        configService.save(vo);
        return Result.ok();
    }
}
