package com.bc.bcblog.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.SecurityPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 安全设置：安全密码、在线会话管理（仅超级管理员，权限由 WebConfig 拦截）。 */
@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final SecurityPasswordService securityPasswordService;
    private final ConfigService configService;
    private final SysUserMapper userMapper;

    /** 安全设置状态 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        Long uid = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(uid);
        data.put("username", user == null ? "-" : user.getUsername());
        data.put("hasSecurityPassword", securityPasswordService.hasPassword(uid));
        data.put("verified", securityPasswordService.isVerified());
        // 安全邮箱：此接口仅超级管理员可访问（WebConfig 里 SUPER_ONLY_PATHS 已限制），
        // 所以这里返回明文方便直接编辑，页面上再自行做展示处理
        data.put("securityEmail", configService.getConfigValue("admin_security_email", ""));
        data.put("securityEmailMask", mask(configService.getConfigValue("admin_security_email", "")));
        data.put("enabled", configService.getConfigValue("admin_security_enabled", "1"));
        // 二次验证有效期内验证一次即可，过期需重新验证
        data.put("verifyMinutes", configService.getConfigValue("admin_security_verify_minutes", "30"));
        // 异常登录提醒相关
        data.put("alertEnabled", configService.getConfigValue("admin_login_alert_enabled", "1"));
        data.put("nightStart", configService.getConfigValue("admin_login_alert_night_start", "00:00"));
        data.put("nightEnd", configService.getConfigValue("admin_login_alert_night_end", "06:00"));
        data.put("alertFailTimes", configService.getConfigValue("admin_login_alert_fail_times", "3"));
        // 单点登录开关
        data.put("singleLogin", configService.getConfigValue("admin_single_login", "1"));
        return Result.ok(data);
    }

    /** 二次验证：输入安全密码（必须先设置过安全密码，否则一律失败） */
    @PostMapping("/verify")
    public Result<Void> verify(@RequestBody Map<String, Object> body) {
        Long uid = StpUtil.getLoginIdAsLong();
        String password = body.get("password") == null ? "" : String.valueOf(body.get("password"));
        if (!securityPasswordService.verify(uid, password)) {
            return Result.fail(400, "安全密码不正确");
        }
        securityPasswordService.markVerified();
        return Result.ok();
    }

    /** 设置 / 修改安全密码 */
    @PostMapping("/password")
    public Result<Void> setPassword(@RequestBody Map<String, Object> body) {
        Long uid = StpUtil.getLoginIdAsLong();
        securityPasswordService.setPassword(uid,
                body.get("loginPassword") == null ? "" : String.valueOf(body.get("loginPassword")),
                body.get("securityPassword") == null ? "" : String.valueOf(body.get("securityPassword")));
        return Result.ok();
    }

    /** 保存安全邮箱等安全设置 */
    @PostMapping("/settings")
    public Result<Void> saveSettings(@RequestBody Map<String, Object> body) {
        saveIfPresent(body, "securityEmail", "admin_security_email", true);
        saveIfPresent(body, "enabled", "admin_security_enabled", false);
        saveIfPresent(body, "alertEnabled", "admin_login_alert_enabled", false);
        saveIfPresent(body, "verifyMinutes", "admin_security_verify_minutes", false);
        saveIfPresent(body, "nightStart", "admin_login_alert_night_start", false);
        saveIfPresent(body, "nightEnd", "admin_login_alert_night_end", false);
        saveIfPresent(body, "alertFailTimes", "admin_login_alert_fail_times", false);
        saveIfPresent(body, "singleLogin", "admin_single_login", false);
        return Result.ok();
    }

    /** 请求体里出现过该字段才落库，避免前端只提交部分字段时把其它配置清空 */
    private void saveIfPresent(Map<String, Object> body, String field, String configKey, boolean trim) {
        if (body.get(field) == null) {
            return;
        }
        String value = String.valueOf(body.get(field));
        configService.setConfigValue(configKey, trim ? value.trim() : value);
    }

    /** 在线会话列表 */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<String> tokens = StpUtil.searchTokenValue("", 0, -1, true);
        for (String token : tokens) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) {
                    continue;
                }
                SysUser user = userMapper.selectById(Long.parseLong(String.valueOf(loginId)));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("token", token.length() > 12 ? token.substring(0, 8) + "..." : token);
                row.put("tokenValue", token);
                row.put("userId", loginId);
                row.put("username", user == null ? "-" : user.getUsername());
                row.put("nickname", user == null ? "-" : user.getNickname());
                row.put("role", user == null ? "-" : user.getRole());
                row.put("device", StpUtil.getSessionByLoginId(loginId).get("device"));
                row.put("loginTime", StpUtil.getSessionByLoginId(loginId).get("loginTime"));
                list.add(row);
            } catch (Exception ignored) {
                // 过期 token 忽略
            }
        }
        return Result.ok(list);
    }

    /** 踢下线（仅踢会话，不封号） */
    @PostMapping("/sessions/kick")
    public Result<Void> kick(@RequestParam String token) {
        StpUtil.kickoutByTokenValue(token);
        return Result.ok();
    }

    private String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "****";
        }
        return value.charAt(0) + "****" + value.substring(at);
    }
}
