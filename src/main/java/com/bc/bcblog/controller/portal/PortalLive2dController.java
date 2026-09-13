package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.Live2dModel;
import com.bc.bcblog.service.Live2dModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台 Live2D 看板娘接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/live2d")
@RequiredArgsConstructor
public class PortalLive2dController {

    private final Live2dModelService modelService;

    @GetMapping("/active")
    public Result<Live2dModel> active() {
        return Result.ok(modelService.active());
    }

    @GetMapping("/list")
    public Result<List<Live2dModel>> list() {
        return Result.ok(modelService.list());
    }
}
