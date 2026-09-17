package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.SecurityPasswordService;
import com.bc.bcblog.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 安全密码实现：BCrypt 存储，验证结果挂在 Sa-Token 会话上。
 *
 * 有效期：以前是「本次登录内一直有效」，等于登录后只要验证过一次，整个会话都不再校验，
 * 一旦浏览器放着不动被别人操作、或者会话本来就已经被标记过，就会看起来像"绕过了验证"。
 * 现在改成**带过期的验证凭据**（默认 30 分钟，可用 admin_security_verify_minutes 调整），
 * 过期后需要重新验证；设置完安全密码的当下仍然直接视为已验证，避免刚设完就要再输一次。
 */
@Service
@RequiredArgsConstructor
public class SecurityPasswordServiceImpl implements SecurityPasswordService {

    private static final String SESSION_KEY = "securityVerified";
    /** 验证有效期（分钟），默认 30；填 0 或负数表示不限期（恢复成旧的"本次登录内有效"） */
    private static final String EXPIRE_CONFIG_KEY = "admin_security_verify_minutes";

    private final SysUserMapper userMapper;
    private final ConfigService configService;

    @Override
    public boolean hasPassword(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        return user != null && user.getSecurityPassword() != null && !user.getSecurityPassword().isEmpty();
    }

    @Override
    public boolean verify(Long userId, String password) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || password == null || password.isEmpty()) {
            return false;
        }
        // 必须真的设置过安全密码才允许二次验证。
        // 以前这里回退用登录密码，等于登录密码就是"安全密码"，浏览器一自动填充就"没输密码也算验证通过"，
        // 二次验证形同虚设（还会让人以为能跳过校验）。
        if (!hasPassword(userId)) {
            return false;
        }
        String target = user.getSecurityPassword();
        try {
            return BCrypt.checkpw(password, target);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setPassword(Long userId, String loginPassword, String newSecurityPassword) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (loginPassword == null || !BCrypt.checkpw(loginPassword, user.getPassword())) {
            throw new BusinessException("登录密码不正确");
        }
        if (newSecurityPassword == null || newSecurityPassword.trim().length() < 6) {
            throw new BusinessException("安全密码至少 6 位");
        }
        if (newSecurityPassword.trim().equals(loginPassword)) {
            throw new BusinessException("安全密码不能与登录密码相同");
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getSecurityPassword, BCrypt.hashpw(newSecurityPassword.trim())));
        markVerified();
    }

    @Override
    public boolean isVerified() {
        try {
            Object value = StpUtil.getSession().get(SESSION_KEY);
            if (value == null) {
                return false;
            }
            // 兼容老数据：以前存的是 Boolean.TRUE
            long verifiedAt;
            if (value instanceof Number) {
                verifiedAt = ((Number) value).longValue();
            } else if (Boolean.TRUE.equals(value)) {
                verifiedAt = System.currentTimeMillis();
            } else {
                return false;
            }
            int minutes = expireMinutes();
            if (minutes <= 0) {
                return true;
            }
            return System.currentTimeMillis() - verifiedAt <= minutes * 60_000L;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void markVerified() {
        try {
            StpUtil.getSession().set(SESSION_KEY, System.currentTimeMillis());
        } catch (Exception ignored) {
            // 非 Web 线程忽略
        }
    }

    /** 验证有效期（分钟）：配置项 admin_security_verify_minutes，默认 30 */
    private int expireMinutes() {
        try {
            String value = configService.getConfigValue(EXPIRE_CONFIG_KEY, "30");
            return value == null || value.trim().isEmpty() ? 30 : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 30;
        }
    }
}
