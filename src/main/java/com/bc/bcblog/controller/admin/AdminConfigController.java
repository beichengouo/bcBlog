package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    /** 上传并设置站点 Logo。 */
    @PostMapping("/logo")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        return Result.ok(configService.uploadLogo(file));
    }

    /** 删除当前 Logo，恢复为默认头像。 */
    @DeleteMapping("/logo")
    public Result<Void> deleteLogo() {
        configService.deleteLogo();
        return Result.ok();
    }

    /** 设置看板娘是否在前台显示。 */
    @PutMapping("/live2d-enabled")
    public Result<Void> setLive2dEnabled(@RequestParam boolean enabled) {
        configService.setLive2dEnabled(enabled);
        return Result.ok();
    }
}
