package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.mapper.SysConfigMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 站点设置服务，基于 sys_config 键值对存储。
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final String KEY_SITE_NAME = "site_name";
    private static final String KEY_SITE_LOGO = "site_logo";
    private static final String KEY_SITE_ICP = "site_icp";
    private static final String KEY_SITE_DESCRIPTION = "site_description";
    private static final String KEY_SITE_KEYWORDS = "site_keywords";

    private final SysConfigMapper configMapper;

    @Override
    public SiteConfigVO get() {
        Map<String, String> map = loadMap();
        SiteConfigVO vo = new SiteConfigVO();
        vo.setSiteName(map.getOrDefault(KEY_SITE_NAME, "bcBlog"));
        vo.setSiteLogo(map.get(KEY_SITE_LOGO));
        vo.setSiteIcp(map.get(KEY_SITE_ICP));
        vo.setSiteDescription(map.get(KEY_SITE_DESCRIPTION));
        vo.setSiteKeywords(map.get(KEY_SITE_KEYWORDS));
        return vo;
    }

    @Override
    public void save(SiteConfigVO vo) {
        upsert(KEY_SITE_NAME, vo.getSiteName());
        upsert(KEY_SITE_LOGO, vo.getSiteLogo());
        upsert(KEY_SITE_ICP, vo.getSiteIcp());
        upsert(KEY_SITE_DESCRIPTION, vo.getSiteDescription());
        upsert(KEY_SITE_KEYWORDS, vo.getSiteKeywords());
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
