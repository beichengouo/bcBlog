package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogPhoto;
import com.bc.bcblog.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台流光忆庭照片管理接口。 */
@RestController
@RequestMapping("/api/admin/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @GetMapping("/list")
    public Result<List<BlogPhoto>> list() {
        return Result.ok(photoService.list());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody BlogPhoto photo) {
        photoService.save(photo);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        photoService.delete(id);
        return Result.ok();
    }
}
