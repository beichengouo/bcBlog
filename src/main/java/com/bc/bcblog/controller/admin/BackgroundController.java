package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.Background;
import com.bc.bcblog.service.BackgroundService;
import com.bc.bcblog.vo.PageBackgroundVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 后台背景壁纸管理接口。 */
@RestController
@RequestMapping("/api/admin/background")
@RequiredArgsConstructor
public class BackgroundController {

    private final BackgroundService backgroundService;

    @GetMapping("/list")
    public Result<List<Background>> list() {
        return Result.ok(backgroundService.list());
    }

    @PostMapping("/upload")
    public Result<Background> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(backgroundService.upload(file));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        backgroundService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/active")
    public Result<Void> setActive(@PathVariable Long id,
                                  @RequestParam(defaultValue = "portal") String scope) {
        backgroundService.setActive(id, scope);
        return Result.ok();
    }

    @PutMapping("/default")
    public Result<Void> clearActive(@RequestParam(defaultValue = "portal") String scope) {
        backgroundService.clearActive(scope);
        return Result.ok();
    }

    /** 前台各页面的独立背景设置（首页 / 流光忆庭 / 智库 / 沙盒世界） */
    @GetMapping("/pages")
    public Result<List<PageBackgroundVO>> pages() {
        return Result.ok(backgroundService.pageSettings());
    }

    @PutMapping("/pages/{pageKey}")
    public Result<Void> savePage(@PathVariable String pageKey, @RequestBody PageSettingDTO body) {
        backgroundService.savePageSetting(pageKey, body.getMode(), body.getBackgroundId(), body.getOpacity());
        return Result.ok();
    }

    /** 页面背景设置请求体 */
    @Data
    public static class PageSettingDTO {
        /** follow / none / custom */
        private String mode;
        private Long backgroundId;
        private Double opacity;
    }
}
