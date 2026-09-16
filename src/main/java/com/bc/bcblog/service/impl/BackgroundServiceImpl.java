package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.Background;
import com.bc.bcblog.entity.PageBackground;
import com.bc.bcblog.mapper.BackgroundMapper;
import com.bc.bcblog.mapper.PageBackgroundMapper;
import com.bc.bcblog.service.BackgroundService;
import com.bc.bcblog.vo.PageBackgroundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final PageBackgroundMapper pageBackgroundMapper;

    /** 支持独立设置背景的前台页面（key 与前端路由对应，顺序就是后台展示顺序） */
    private static final Map<String, String> PAGE_LABELS = new LinkedHashMap<>();

    static {
        PAGE_LABELS.put("home", "首页");
        PAGE_LABELS.put("photos", "流光忆庭");
        PAGE_LABELS.put("resources", "智库");
        PAGE_LABELS.put("sandbox", "沙盒世界");
        // 「其它前台页面」（文章详情、用户中心等）也有自己的一行，方便统一调透明度
        PAGE_LABELS.put("portal", "其它前台页面");
    }

    /** 「其它前台页面」的页面标识：没传 page 或页面未知时都用它 */
    private static final String PAGE_DEFAULT = "portal";

    private static final String MODE_FOLLOW = "follow";
    private static final String MODE_NONE = "none";
    private static final String MODE_CUSTOM = "custom";

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

    // ============================== 前台各页面独立背景 ==============================

    @Override
    public List<PageBackgroundVO> pageSettings() {
        List<PageBackgroundVO> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : PAGE_LABELS.entrySet()) {
            PageBackground row = pageBackgroundMapper.selectById(entry.getKey());
            PageBackgroundVO vo = new PageBackgroundVO();
            vo.setPageKey(entry.getKey());
            vo.setLabel(entry.getValue());
            vo.setMode(row == null || row.getMode() == null ? MODE_FOLLOW : row.getMode());
            vo.setBackgroundId(row == null ? null : row.getBackgroundId());
            vo.setOpacity(row == null || row.getOpacity() == null ? 1d : row.getOpacity().doubleValue());
            fillEffectiveBackground(vo);
            list.add(vo);
        }
        return list;
    }

    @Override
    public PageBackgroundVO pageBackground(String pageKey) {
        // 没传页面、或页面不在可配置清单里（文章详情等）→ 用「其它前台页面」这一行
        String key = pageKey != null && PAGE_LABELS.containsKey(pageKey) ? pageKey : PAGE_DEFAULT;
        PageBackgroundVO vo = new PageBackgroundVO();
        vo.setPageKey(pageKey);
        PageBackground row = pageBackgroundMapper.selectById(key);
        vo.setMode(row == null || row.getMode() == null ? MODE_FOLLOW : row.getMode());
        vo.setBackgroundId(row == null ? null : row.getBackgroundId());
        vo.setOpacity(row == null || row.getOpacity() == null ? 1d : row.getOpacity().doubleValue());
        fillEffectiveBackground(vo);
        return vo;
    }

    /** 根据 mode 算出实际生效的壁纸：custom 用指定壁纸，follow 用前台默认壁纸，none 什么都不用 */
    private void fillEffectiveBackground(PageBackgroundVO vo) {
        Background chosen = null;
        if (MODE_CUSTOM.equals(vo.getMode()) && vo.getBackgroundId() != null) {
            chosen = backgroundMapper.selectById(vo.getBackgroundId());
            if (chosen == null) {
                // 壁纸被删掉了，退回跟随默认，避免前台白屏
                vo.setMode(MODE_FOLLOW);
            }
        }
        if (chosen == null && MODE_FOLLOW.equals(vo.getMode())) {
            chosen = activeByScope("portal");
        }
        if (chosen != null) {
            vo.setUrl(chosen.getUrl());
            vo.setType(chosen.getType());
        }
    }

    @Override
    public PageBackgroundVO resolve(String scope, String pageKey) {
        if (!"admin".equals(scope)) {
            return pageBackground(pageKey);
        }
        // 后台壁纸仍然只看「后台使用中」那张，不参与页面级设置
        PageBackgroundVO vo = new PageBackgroundVO();
        vo.setPageKey(pageKey);
        Background admin = activeByScope("admin");
        vo.setMode(admin == null ? MODE_NONE : MODE_CUSTOM);
        vo.setOpacity(1d);
        if (admin != null) {
            vo.setBackgroundId(admin.getId());
            vo.setUrl(admin.getUrl());
            vo.setType(admin.getType());
        }
        return vo;
    }

    @Override
    public void savePageSetting(String pageKey, String mode, Long backgroundId, Double opacity) {
        if (pageKey == null || !PAGE_LABELS.containsKey(pageKey)) {
            throw new BusinessException("不支持为这个页面单独设置背景");
        }
        String safeMode = MODE_CUSTOM.equals(mode) || MODE_NONE.equals(mode) ? mode : MODE_FOLLOW;
        if (MODE_CUSTOM.equals(safeMode)) {
            if (backgroundId == null || backgroundMapper.selectById(backgroundId) == null) {
                throw new BusinessException("选择的背景不存在，请重新选择");
            }
        } else {
            backgroundId = null;
        }
        double safeOpacity = clampOpacity(opacity);
        PageBackground row = pageBackgroundMapper.selectById(pageKey);
        if (row == null) {
            PageBackground created = new PageBackground();
            created.setPageKey(pageKey);
            created.setMode(safeMode);
            created.setBackgroundId(backgroundId);
            created.setOpacity(BigDecimal.valueOf(safeOpacity));
            pageBackgroundMapper.insert(created);
            return;
        }
        // 注意：backgroundId 可能要被清空，updateById 会忽略 null，所以用 update wrapper 显式写
        LambdaUpdateWrapper<PageBackground> uw = new LambdaUpdateWrapper<PageBackground>()
                .eq(PageBackground::getPageKey, pageKey)
                .set(PageBackground::getMode, safeMode)
                .set(PageBackground::getBackgroundId, backgroundId)
                .set(PageBackground::getOpacity, BigDecimal.valueOf(safeOpacity));
        pageBackgroundMapper.update(null, uw);
    }

    /** 不透明度限制在 0.10~1.00 */
    private double clampOpacity(Double opacity) {
        double value = opacity == null ? 1d : opacity;
        value = Math.max(0.1d, Math.min(1d, value));
        return Math.round(value * 100d) / 100d;
    }
}
