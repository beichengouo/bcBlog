package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.mapper.SysConfigMapper;
import com.bc.bcblog.service.DeepseekService;
import com.bc.bcblog.vo.DeepseekBalanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 余额查询服务，API Key 存储在 sys_config 表中。
 */
@Service
@RequiredArgsConstructor
public class DeepseekServiceImpl implements DeepseekService {

    private static final String KEY = "deepseek_api_key";
    private static final String URL = "https://api.deepseek.com/user/balance";

    private final SysConfigMapper configMapper;

    @Override
    public String getApiKey() {
        SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, KEY));
        return c == null ? null : c.getConfigValue();
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("API Key 不能为空");
        }
        SysConfig existing = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, KEY));
        if (existing == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(KEY);
            c.setConfigValue(apiKey.trim());
            configMapper.insert(c);
        } else {
            existing.setConfigValue(apiKey.trim());
            configMapper.updateById(existing);
        }
    }

    @Override
    public DeepseekBalanceVO queryBalance() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("请先配置 DeepSeek API Key");
        }

        HttpResponse resp;
        try {
            resp = HttpRequest.get(URL)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Accept", "application/json")
                    .timeout(10000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("余额查询失败：" + e.getMessage());
        }

        if (resp.getStatus() == 401 || resp.getStatus() == 403) {
            throw new BusinessException("API Key 无效或无权限");
        }
        if (resp.getStatus() != 200) {
            throw new BusinessException("余额查询失败（HTTP " + resp.getStatus() + "）");
        }
        return parse(resp.body());
    }

    private DeepseekBalanceVO parse(String body) {
        JSONObject json = JSONUtil.parseObj(body);
        DeepseekBalanceVO vo = new DeepseekBalanceVO();
        vo.setAvailable(json.getBool("is_available", false));

        JSONArray arr = json.getJSONArray("balance_infos");
        List<DeepseekBalanceVO.BalanceInfo> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DeepseekBalanceVO.BalanceInfo info = new DeepseekBalanceVO.BalanceInfo();
                info.setCurrency(o.getStr("currency"));
                info.setTotalBalance(o.getStr("total_balance"));
                info.setGrantedBalance(o.getStr("granted_balance"));
                info.setToppedUpBalance(o.getStr("topped_up_balance"));
                list.add(info);
            }
        }
        vo.setBalanceInfos(list);
        return vo;
    }
}
