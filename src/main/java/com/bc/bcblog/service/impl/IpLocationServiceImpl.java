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

/** IP 定位实现，支持百度地图和高德地图两种方式，由后台配置切换。 */
@Service
@RequiredArgsConstructor
public class IpLocationServiceImpl implements IpLocationService {

    private static final String BAIDU_URL = "https://api.map.baidu.com/location/ip";
    private static final String GAODE_URL = "https://restapi.amap.com/v3/ip";

    private final ConfigService configService;

    @Override
    public String query(String ip) {
        String provider = configService.getIpLocationProvider();
        return "gaode".equals(provider) ? queryGaode(ip) : queryBaidu(ip);
    }

    private String queryBaidu(String ip) {
        String ak = configService.getIpLocationAk();
        if (ak == null || ak.trim().isEmpty()) {
            throw new BusinessException("请先在“第三方接口”中配置百度 IP 定位 AK");
        }

        HttpResponse resp;
        try {
            resp = HttpRequest.get(BAIDU_URL)
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

    private String queryGaode(String ip) {
        String key = configService.getGaodeIpKey();
        if (key == null || key.trim().isEmpty()) {
            throw new BusinessException("请先在“第三方接口”中配置高德 IP 定位 Key");
        }

        HttpResponse resp;
        try {
            resp = HttpRequest.get(GAODE_URL)
                    .form("key", key.trim())
                    .form("ip", ip == null ? "" : ip.trim())
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
            String status = json.getStr("status");
            if (!"1".equals(status)) {
                String info = json.getStr("info");
                String infocode = json.getStr("infocode");
                throw new BusinessException("IP 定位查询失败：" + (info == null || info.trim().isEmpty()
                        ? "高德接口返回异常" : info)
                        + (infocode == null || infocode.trim().isEmpty() ? "" : "（" + infocode + "）"));
            }
            // 高德返回的省、市可能为空，优先返回市，市为空时返回省
            String city = json.getStr("city");
            String province = json.getStr("province");
            String address = city == null || city.trim().isEmpty() ? province : city;
            return address == null || address.trim().isEmpty() ? "未知位置" : address;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("IP 定位结果解析失败：" + e.getMessage());
        }
    }
}
