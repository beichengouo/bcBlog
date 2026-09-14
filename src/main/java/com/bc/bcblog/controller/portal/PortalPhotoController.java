package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogPhoto;
import com.bc.bcblog.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台流光忆庭照片接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/photo")
@RequiredArgsConstructor
public class PortalPhotoController {

    private final PhotoService photoService;

    @GetMapping("/list")
    public Result<List<BlogPhoto>> list() {
        return Result.ok(photoService.list());
    }
}
