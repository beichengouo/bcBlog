package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.Background;
import com.bc.bcblog.mapper.BackgroundMapper;
import com.bc.bcblog.service.BackgroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** 背景壁纸服务：共用同一个壁纸库，前台和后台分别独立启用。 */
@Service
@RequiredArgsConstructor
public class BackgroundServiceImpl implements BackgroundService {

    @Value("${bcblog.upload-dir:./uploads}")
    private String uploadDir;

    private static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final List<String> VIDEO_EXT = Arrays.asList("mp4", "webm", "mov");
    private static final long MAX_SIZE = 200 * 1024 * 1024L;

    private final BackgroundMapper backgroundMapper;

    @Override
    public List<Background> list() {
        return backgroundMapper.selectList(new LambdaQueryWrapper<Background>()
                .orderByAsc(Background::getId));
    }

    @Override
    public Background upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        String type;
        if (IMAGE_EXT.contains(ext)) {
            type = "image";
        } else if (VIDEO_EXT.contains(ext)) {
            type = "video";
        } else {
            throw new BusinessException("仅支持图片(jpg/png/webp/gif/bmp)或视频(mp4/webm/mov)");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件大小不能超过 200MB");
        }

        File dir = new File(uploadDir, "background");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建背景目录失败");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            file.transferTo(new File(dir, filename).getAbsoluteFile());
        } catch (Exception e) {
            throw new BusinessException("文件保存失败：" + e.getMessage());
        }

        Background b = new Background();
        b.setName(original == null ? filename : original);
        b.setType(type);
        b.setUrl("/uploads/background/" + filename);
        b.setPortalActive(0);
        b.setAdminActive(0);
        backgroundMapper.insert(b);
        return b;
    }

    @Override
    public void delete(Long id) {
        Background b = backgroundMapper.selectById(id);
        if (b == null) {
            return;
        }
        backgroundMapper.deleteById(id);
        try {
            String path = b.getUrl().substring(b.getUrl().lastIndexOf("/uploads/") + "/uploads/".length());
            File f = new File(uploadDir, path).getAbsoluteFile();
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void setActive(Long id, String scope) {
        Background b = backgroundMapper.selectById(id);
        if (b == null) {
            throw new BusinessException("背景不存在");
        }
        boolean admin = "admin".equals(scope);
        LambdaUpdateWrapper<Background> uw = new LambdaUpdateWrapper<>();
        if (admin) {
            uw.set(Background::getAdminActive, 0);
        } else {
            uw.set(Background::getPortalActive, 0);
        }
        backgroundMapper.update(null, uw);
        Background upd = new Background();
        upd.setId(id);
        if (admin) {
            upd.setAdminActive(1);
        } else {
            upd.setPortalActive(1);
        }
        backgroundMapper.updateById(upd);
    }

    @Override
    public Background activeByScope(String scope) {
        LambdaQueryWrapper<Background> wrapper = new LambdaQueryWrapper<>();
        if ("admin".equals(scope)) {
            wrapper.eq(Background::getAdminActive, 1);
        } else {
            wrapper.eq(Background::getPortalActive, 1);
        }
        return backgroundMapper.selectOne(wrapper.last("limit 1"));
    }

    @Override
    public void clearActive(String scope) {
        LambdaUpdateWrapper<Background> uw = new LambdaUpdateWrapper<>();
        if ("admin".equals(scope)) {
            uw.set(Background::getAdminActive, 0);
        } else {
            uw.set(Background::getPortalActive, 0);
        }
        backgroundMapper.update(null, uw);
    }
}
