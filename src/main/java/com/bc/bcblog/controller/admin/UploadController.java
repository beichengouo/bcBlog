package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台文件上传接口。
 */
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /** 上传图片，返回可访问的 URL 路径 */
    @PostMapping("/image")
    public Result<String> image(@RequestParam("file") MultipartFile file) {
        return Result.ok(uploadService.uploadImage(file));
    }
}
