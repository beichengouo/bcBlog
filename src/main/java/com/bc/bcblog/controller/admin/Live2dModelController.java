package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.Live2dModel;
import com.bc.bcblog.service.Live2dModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台 Live2D 看板娘模型管理接口。 */
@RestController
@RequestMapping("/api/admin/live2d")
@RequiredArgsConstructor
public class Live2dModelController {

    private final Live2dModelService modelService;

    @GetMapping("/list")
    public Result<List<Live2dModel>> list() {
        return Result.ok(modelService.list());
    }

    @PostMapping
    public Result<Live2dModel> add(@RequestBody Live2dModel model) {
        return Result.ok(modelService.add(model));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/active")
    public Result<Void> setActive(@PathVariable Long id) {
        modelService.setActive(id);
        return Result.ok();
    }
}
