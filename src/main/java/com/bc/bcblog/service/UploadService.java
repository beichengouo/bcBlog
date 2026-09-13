package com.bc.bcblog.service;

import com.bc.bcblog.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 本地文件上传服务：图片保存到配置的本地目录，返回可访问的 URL 路径。
 */
@Service
public class UploadService {

    /** 上传目录，可在 application.yml 中通过 bcblog.upload-dir 覆盖 */
    @Value("${bcblog.upload-dir:./uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }

        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/gif/webp/bmp 格式");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建上传目录失败");
        }

        // 用 UUID 重命名，避免文件名冲突和路径注入
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            file.transferTo(new File(dir, filename).getAbsoluteFile());
        } catch (Exception e) {
            throw new BusinessException("图片保存失败：" + e.getMessage());
        }
        return "/uploads/" + filename;
    }
}
