package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogResource;
import com.bc.bcblog.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台智库资源接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/resource")
@RequiredArgsConstructor
public class PortalResourceController {

    private final ResourceService resourceService;

    @GetMapping("/list")
    public Result<List<BlogResource>> list() {
        return Result.ok(resourceService.list());
    }
}
