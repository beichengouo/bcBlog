package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.mapper.SysConfigMapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 站点设置服务，基于 sys_config 键值对存储。
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    /** 默认 Logo 文件，提前放在 uploads/logo 目录下。 */
    private static final String DEFAULT_LOGO_URL = "/uploads/logo/avatar.png";
    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final long MAX_LOGO_SIZE = 5 * 1024 * 1024L;

    private static final String KEY_SITE_NAME = "site_name";
    private static final String KEY_SITE_LOGO = "site_logo";
    private static final String KEY_SITE_ICP = "site_icp";
    private static final String KEY_SITE_DESCRIPTION = "site_description";
    private static final String KEY_SITE_KEYWORDS = "site_keywords";
    private static final String KEY_SITE_SLOGAN = "site_slogan";
    private static final String KEY_WEATHER_CITY = "weather_city";
    private static final String KEY_HITOKOTO_CATEGORIES = "hitokoto_categories";
    private static final String KEY_LIVE2D_ENABLED = "live2d_enabled";
    private static final String KEY_IP_LOCATION_AK = "ip_location_ak";
    private static final String KEY_IP_LOCATION_PROVIDER = "ip_location_provider";
    private static final String KEY_GAODE_IP_KEY = "gaode_ip_location_key";
    private static final String KEY_ADMIN_BG_OPACITY = "admin_bg_opacity";
    private static final String KEY_ACG_COVER_TOKEN = "acg_cover_token";
    private static final String KEY_HOME_CAROUSEL_ENABLED = "home_carousel_enabled";
    private static final String KEY_HOME_CAROUSEL_COUNT = "home_carousel_count";

    private final SysConfigMapper configMapper;

    @Value("${bcblog.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public SiteConfigVO get() {
        Map<String, String> map = loadMap();
        SiteConfigVO vo = new SiteConfigVO();
        vo.setSiteName(map.getOrDefault(KEY_SITE_NAME, "bcBlog"));
        vo.setSiteLogo(map.get(KEY_SITE_LOGO));
        vo.setSiteIcp(map.get(KEY_SITE_ICP));
        vo.setSiteDescription(map.get(KEY_SITE_DESCRIPTION));
        vo.setSiteKeywords(map.get(KEY_SITE_KEYWORDS));
        vo.setSiteSlogan(map.get(KEY_SITE_SLOGAN));
        vo.setWeatherCity(map.getOrDefault(KEY_WEATHER_CITY, "北京"));
        vo.setHitokotoCategories(map.getOrDefault(KEY_HITOKOTO_CATEGORIES, "d,i,k"));
        vo.setLive2dEnabled("0".equals(map.get(KEY_LIVE2D_ENABLED)) ? 0 : 1);
        vo.setHomeCarouselEnabled("0".equals(map.get(KEY_HOME_CAROUSEL_ENABLED)) ? 0 : 1);
        vo.setHomeCarouselCount(parseCarouselCount(map.get(KEY_HOME_CAROUSEL_COUNT)));
        return vo;
    }

    @Override
    public void save(SiteConfigVO vo) {
        upsert(KEY_SITE_NAME, vo.getSiteName());
        upsert(KEY_SITE_LOGO, vo.getSiteLogo());
        upsert(KEY_SITE_ICP, vo.getSiteIcp());
        upsert(KEY_SITE_DESCRIPTION, vo.getSiteDescription());
        upsert(KEY_SITE_KEYWORDS, vo.getSiteKeywords());
        upsert(KEY_SITE_SLOGAN, vo.getSiteSlogan());
        // 天气城市和一言分类已经拆到“第三方接口”页维护，这里仅在传入非空时才更新，避免被“系统设置”保存时清空
        if (vo.getWeatherCity() != null) {
            upsert(KEY_WEATHER_CITY, vo.getWeatherCity());
        }
        if (vo.getHitokotoCategories() != null) {
            upsert(KEY_HITOKOTO_CATEGORIES, vo.getHitokotoCategories());
        }
        if (vo.getLive2dEnabled() != null) {
            upsert(KEY_LIVE2D_ENABLED, vo.getLive2dEnabled() == 1 ? "1" : "0");
        }
        if (vo.getHomeCarouselEnabled() != null) {
            upsert(KEY_HOME_CAROUSEL_ENABLED, vo.getHomeCarouselEnabled() == 1 ? "1" : "0");
        }
        if (vo.getHomeCarouselCount() != null) {
            upsert(KEY_HOME_CAROUSEL_COUNT, String.valueOf(parseCarouselCount(String.valueOf(vo.getHomeCarouselCount()))));
        }
    }

    /** 首页轮播数量限制在 1 ~ 10，未配置时默认 5。 */
    private int parseCarouselCount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 5;
        }
        try {
            int count = Integer.parseInt(value.trim());
            return Math.max(1, Math.min(10, count));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    @Override
    public String uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Logo 图片");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/gif/webp/bmp 格式");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new BusinessException("Logo 图片大小不能超过 5MB");
        }

        File dir = new File(uploadDir, "logo");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建 Logo 目录失败");
        }
        // 上传新图前先删除旧的已上传 Logo，但保留默认头像
        deleteUploadedLogoFile();

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            file.transferTo(new File(dir, filename).getAbsoluteFile());
        } catch (Exception e) {
            throw new BusinessException("Logo 保存失败：" + e.getMessage());
        }
        String url = "/uploads/logo/" + filename;
        upsert(KEY_SITE_LOGO, url);
        return url;
    }

    @Override
    public void deleteLogo() {
        deleteUploadedLogoFile();
        upsert(KEY_SITE_LOGO, DEFAULT_LOGO_URL);
    }

    @Override
    public void setLive2dEnabled(boolean enabled) {
        upsert(KEY_LIVE2D_ENABLED, enabled ? "1" : "0");
    }

    @Override
    public boolean isLive2dEnabled() {
        return !"0".equals(loadMap().get(KEY_LIVE2D_ENABLED));
    }

    @Override
    public String getIpLocationAk() {
        return loadMap().get(KEY_IP_LOCATION_AK);
    }

    @Override
    public void setIpLocationAk(String ak) {
        String value = ak == null ? "" : ak.trim();
        upsert(KEY_IP_LOCATION_AK, value);
    }

    @Override
    public String getIpLocationProvider() {
        String provider = loadMap().get(KEY_IP_LOCATION_PROVIDER);
        return "gaode".equals(provider) ? "gaode" : "baidu";
    }

    @Override
    public void setIpLocationProvider(String provider) {
        String normalized = "gaode".equals(provider) ? "gaode" : "baidu";
        upsert(KEY_IP_LOCATION_PROVIDER, normalized);
    }

    @Override
    public String getGaodeIpKey() {
        return loadMap().get(KEY_GAODE_IP_KEY);
    }

    @Override
    public void setGaodeIpKey(String key) {
        upsert(KEY_GAODE_IP_KEY, key == null ? "" : key.trim());
    }

    @Override
    public double getAdminBgOpacity() {
        String value = loadMap().get(KEY_ADMIN_BG_OPACITY);
        if (value == null || value.trim().isEmpty()) {
            return 1.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    @Override
    public void setAdminBgOpacity(double opacity) {
        if (opacity < 0.1 || opacity > 1.0) {
            throw new BusinessException("背景透明度必须在 0.1 到 1.0 之间");
        }
        upsert(KEY_ADMIN_BG_OPACITY, String.valueOf(opacity));
    }

    @Override
    public String getAcgCoverToken() {
        return loadMap().get(KEY_ACG_COVER_TOKEN);
    }

    @Override
    public void setAcgCoverToken(String token) {
        upsert(KEY_ACG_COVER_TOKEN, token == null ? "" : token.trim());
    }

    /** 删除当前设置中已上传的 Logo 文件，默认头像不删除。 */
    private void deleteUploadedLogoFile() {
        SiteConfigVO vo = get();
        String current = vo.getSiteLogo();
        if (current == null || current.equals(DEFAULT_LOGO_URL) || !current.startsWith("/uploads/logo/")) {
            return;
        }
        String name = current.substring("/uploads/logo/".length());
        File f = new File(new File(uploadDir, "logo"), name).getAbsoluteFile();
        if (f.exists()) {
            f.delete();
        }
    }

    private Map<String, String> loadMap() {
        Map<String, String> map = new HashMap<>();
        for (SysConfig c : configMapper.selectList(null)) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }

    /** 不存在则插入，存在则更新 */
    private void upsert(String key, String value) {
        SysConfig existing = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (existing == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            configMapper.insert(c);
        } else {
            existing.setConfigValue(value);
            configMapper.updateById(existing);
        }
    }
}
