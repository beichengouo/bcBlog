package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.service.AcgCoverService;
import com.bc.bcblog.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** ACG 随机封面实现，调用 ALAPI 的 ACG 图片接口。 */
@Service
@RequiredArgsConstructor
public class AcgCoverServiceImpl implements AcgCoverService {

    private static final String URL = "https://v2.alapi.cn/api/acg";

    private final ConfigService configService;

    @Override
    public String randomCover() {
        String token = configService.getAcgCoverToken();
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException("请先在“第三方接口”中配置 ACG 封面 Token");
        }

        HttpResponse resp;
        try {
            resp = HttpRequest.get(URL)
                    .form("token", token.trim())
                    .form("format", "json")
                    .timeout(15000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("随机封面获取失败：" + e.getMessage());
        }

        if (resp.getStatus() != 200) {
            throw new BusinessException("随机封面获取失败（HTTP " + resp.getStatus() + "）");
        }
        try {
            JSONObject json = JSONUtil.parseObj(resp.body());
            if (json.getInt("code", -1) != 200) {
                throw new BusinessException("随机封面获取失败：" + json.getStr("message", "接口返回异常"));
            }
            JSONObject data = json.getJSONObject("data");
            String url = data == null ? null : data.getStr("url");
            if (url == null || url.trim().isEmpty()) {
                throw new BusinessException("随机封面获取失败：未返回图片地址");
            }
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("随机封面结果解析失败：" + e.getMessage());
        }
    }
}
