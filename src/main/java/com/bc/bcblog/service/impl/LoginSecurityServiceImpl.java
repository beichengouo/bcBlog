package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.entity.SysLoginIp;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysLoginIpMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.EmailService;
import com.bc.bcblog.service.IpLocationService;
import com.bc.bcblog.service.LoginSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录安全实现。
 *
 * 设计要点：
 *   1. 「IP 是否变化」完全本地比对，不做网络调用；
 *   2. 只有出现陌生 IP / 异地时才查询一次归属地，并把结果缓存到 sys_login_ip；
 *   3. 同一 IP 24 小时内只发一次异常提醒，避免反复尝试时刷邮件；
 *   4. 所有异常都不阻断登录（只提醒）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSecurityServiceImpl implements LoginSecurityService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfigService configService;
    private final EmailService emailService;
    private final IpLocationService ipLocationService;
    private final SysLoginIpMapper loginIpMapper;

    @Override
    public void afterLoginSuccess(SysUser user, String ip, String userAgent) {
        try {
            if (!securityEnabled()) {
                return;
            }
            SysLoginIp record = find(user.getId(), ip);
            boolean firstIp = record == null;
            boolean nightTime = inNightNow();

            // 只记最近一次登录 IP，用于下次判断「变化」
            if (record == null) {
                record = new SysLoginIp();
                record.setUserId(user.getId());
                record.setIp(ip);
                record.setLoginCount(1);
                record.setLastLoginTime(LocalDateTime.now());
                loginIpMapper.insert(record);
            } else {
                record.setLoginCount((record.getLoginCount() == null ? 0 : record.getLoginCount()) + 1);
                record.setLastLoginTime(LocalDateTime.now());
                loginIpMapper.updateById(record);
            }

            // 是否属于异常：新 IP（第一次在该 IP 登录）或深夜时段
            if (!"1".equals(configService.getConfigValue("admin_login_alert_enabled", "1"))) {
                return;
            }
            boolean abnormal = firstIp || nightTime;
            if (!abnormal) {
                return;
            }
            // 只有陌生 IP 才查询归属地（带缓存）
            String region = record.getRegion();
            if ((region == null || region.isEmpty()) && !isPrivateIp(ip)) {
                region = safeRegion(ip);
                if (region != null && !region.isEmpty()) {
                    record.setRegion(region);
                    loginIpMapper.updateById(record);
                }
            }
            // 同一 IP 24 小时内只发一次
            LocalDateTime lastNotify = record.getLastNotifyTime();
            if (lastNotify != null && lastNotify.isAfter(LocalDateTime.now().minusHours(24))) {
                return;
            }
            sendAlert(user, ip, region, userAgent, "登录成功",
                    firstIp ? "首次在该 IP 登录" : "深夜时段登录");
            record.setLastNotifyTime(LocalDateTime.now());
            loginIpMapper.updateById(record);
        } catch (Exception e) {
            log.warn("异常登录检测失败：{}", e.getMessage());
        }
    }

    @Override
    public void afterLoginFail(SysUser user, String ip, String userAgent, String reason) {
        try {
            if (!securityEnabled() || user == null) {
                return;
            }
            SysLoginIp record = find(user.getId(), ip);
            // 同一个 IP 1 小时内只提醒一次，避免被反复尝试刷邮件
            if (record != null && record.getLastNotifyTime() != null
                    && record.getLastNotifyTime().isAfter(LocalDateTime.now().minusHours(1))) {
                return;
            }
            String region = record == null ? safeRegion(ip) : record.getRegion();
            sendAlert(user, ip, region, userAgent, "登录失败", reason);
            if (record == null) {
                record = new SysLoginIp();
                record.setUserId(user.getId());
                record.setIp(ip);
                record.setLoginCount(0);
                record.setLastNotifyTime(LocalDateTime.now());
                loginIpMapper.insert(record);
            } else {
                record.setLastNotifyTime(LocalDateTime.now());
                loginIpMapper.updateById(record);
            }
        } catch (Exception e) {
            log.warn("登录失败提醒发送失败：{}", e.getMessage());
        }
    }

    private void sendAlert(SysUser user, String ip, String region, String userAgent,
                           String result, String reason) {
        String email = configService.getConfigValue("admin_security_email", "");
        if (email == null || email.trim().isEmpty()) {
            log.warn("未配置安全邮箱，跳过异常登录提醒（账号：{}，IP：{}，原因：{}）", user.getUsername(), ip, reason);
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("siteName", configService.getConfigValue("site_name", "圣樱"));
        vars.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vars.put("username", user.getUsername());
        vars.put("ip", ip);
        vars.put("region", region == null || region.isEmpty() ? "未知地区" : region);
        vars.put("browser", shorten(userAgent));
        vars.put("result", result);
        vars.put("reason", reason == null ? "" : reason);
        try {
            emailService.sendTemplate("login_alert", email.trim(), vars);
        } catch (Exception e) {
            log.warn("异常登录提醒邮件发送失败：{}", e.getMessage());
        }
    }

    private SysLoginIp find(Long userId, String ip) {
        return loginIpMapper.selectOne(new LambdaQueryWrapper<SysLoginIp>()
                .eq(SysLoginIp::getUserId, userId)
                .eq(SysLoginIp::getIp, ip)
                .last("limit 1"));
    }

    private boolean securityEnabled() {
        return "1".equals(configService.getConfigValue("admin_security_enabled", "1"));
    }

    private boolean inNightNow() {
        String start = configService.getConfigValue("admin_login_alert_night_start", "00:00");
        String end = configService.getConfigValue("admin_login_alert_night_end", "06:00");
        try {
            LocalTime s = LocalTime.parse(start.trim(), TIME_FORMATTER);
            LocalTime e = LocalTime.parse(end.trim(), TIME_FORMATTER);
            LocalTime now = LocalTime.now();
            if (s.equals(e)) {
                return false;
            }
            if (s.isBefore(e)) {
                return !now.isBefore(s) && now.isBefore(e);
            }
            return !now.isBefore(s) || now.isBefore(e);
        } catch (Exception ex) {
            return false;
        }
    }

    private String safeRegion(String ip) {
        try {
            return ipLocationService.query(ip);
        } catch (Exception e) {
            return "未知地区";
        }
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null) {
            return true;
        }
        return ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private String shorten(String userAgent) {
        if (userAgent == null) {
            return "未知";
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }

}
