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
        data.put("hasSecurityPassword", securityPasswordService.hasPassword(uid));
        data.put("verified", securityPasswordService.isVerified());
        data.put("securityEmail", mask(configService.getConfigValue("admin_security_email", "")));
        data.put("enabled", configService.getConfigValue("admin_security_enabled", "1"));
        return Result.ok(data);
    }

    /** 二次验证：输入安全密码（未设置过安全密码时用登录密码） */
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
        if (body.get("securityEmail") != null) {
            configService.setConfigValue("admin_security_email", String.valueOf(body.get("securityEmail")).trim());
        }
        if (body.get("enabled") != null) {
            configService.setConfigValue("admin_security_enabled", String.valueOf(body.get("enabled")));
        }
        if (body.get("alertEnabled") != null) {
            configService.setConfigValue("admin_login_alert_enabled", String.valueOf(body.get("alertEnabled")));
        }
        return Result.ok();
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
