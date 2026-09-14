package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogResource;
import com.bc.bcblog.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台智库资源管理接口。 */
@RestController
@RequestMapping("/api/admin/resource")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/list")
    public Result<List<BlogResource>> list() {
        return Result.ok(resourceService.list());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody BlogResource resource) {
        resourceService.save(resource);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return Result.ok();
    }
}
