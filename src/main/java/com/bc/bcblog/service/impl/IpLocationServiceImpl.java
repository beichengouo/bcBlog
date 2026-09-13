package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 百度普通 IP 定位实现。 */
@Service
@RequiredArgsConstructor
public class IpLocationServiceImpl implements IpLocationService {

    private static final String URL = "https://api.map.baidu.com/location/ip";

    private final ConfigService configService;

    @Override
    public String query(String ip) {
        String ak = configService.getIpLocationAk();
        if (ak == null || ak.trim().isEmpty()) {
            throw new BusinessException("请先在“第三方接口”中配置百度 IP 定位 AK");
        }

        HttpResponse resp;
        try {
            resp = HttpRequest.get(URL)
                    .form("ak", ak.trim())
                    .form("ip", ip == null ? "" : ip.trim())
                    .form("coor", "bd09ll")
                    .timeout(10000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("IP 定位查询失败：" + e.getMessage());
        }

        if (resp.getStatus() != 200) {
            throw new BusinessException("IP 定位查询失败（HTTP " + resp.getStatus() + "）");
        }

        try {
            JSONObject json = JSONUtil.parseObj(resp.body());
            int status = json.getInt("status", -1);
            if (status != 0) {
                String message = json.getStr("message");
                throw new BusinessException("IP 定位查询失败：" + (message == null || message.trim().isEmpty()
                        ? "请检查 AK 是否有效" : message));
            }
            JSONObject content = json.getJSONObject("content");
            if (content != null) {
                String address = content.getStr("address");
                if (address != null && !address.trim().isEmpty()) {
                    return address;
                }
            }
            String address = json.getStr("address");
            return address == null || address.trim().isEmpty() ? "未知位置" : address;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("IP 定位结果解析失败：" + e.getMessage());
        }
    }
}
