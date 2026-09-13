package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.Background;
import com.bc.bcblog.service.BackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
