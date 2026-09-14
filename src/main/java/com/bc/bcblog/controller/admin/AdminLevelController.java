package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SysLevel;
import com.bc.bcblog.service.LevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台等级配置接口。 */
@RestController
@RequestMapping("/api/admin/level")
@RequiredArgsConstructor
public class AdminLevelController {

    private final LevelService levelService;

    @GetMapping("/list")
    public Result<List<SysLevel>> list() {
        return Result.ok(levelService.list());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody SysLevel level) {
        levelService.save(level);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        levelService.delete(id);
        return Result.ok();
    }
}
