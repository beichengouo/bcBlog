package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.SecurityPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 安全密码实现：BCrypt 存储，验证结果挂在 Sa-Token 会话上（本次登录内有效）。 */
@Service
@RequiredArgsConstructor
public class SecurityPasswordServiceImpl implements SecurityPasswordService {

    private static final String SESSION_KEY = "securityVerified";

    private final SysUserMapper userMapper;

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
        // 没设置安全密码时回退用登录密码，避免把自己锁住
        String target = hasPassword(userId) ? user.getSecurityPassword() : user.getPassword();
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
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void markVerified() {
        try {
            StpUtil.getSession().set(SESSION_KEY, Boolean.TRUE);
        } catch (Exception ignored) {
            // 非 Web 线程忽略
        }
    }
}
