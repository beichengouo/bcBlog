package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.dto.LoginDTO;
import com.bc.bcblog.entity.SysLoginLog;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysLoginLogMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AuthService;
import com.bc.bcblog.vo.CaptchaVO;
import com.bc.bcblog.vo.LoginResultVO;
import com.bc.bcblog.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_IP_PER_MINUTE = 20;
    private static final long CAPTCHA_EXPIRE_MILLIS = 5 * 60 * 1000L;

    private final SysUserMapper sysUserMapper;
    private final SysLoginLogMapper sysLoginLogMapper;

    private final ConcurrentHashMap<String, CaptchaItem> captchaMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> failCountMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpWindow> ipWindowMap = new ConcurrentHashMap<>();

    @Override
    public CaptchaVO captcha() {
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(120, 40, 4, 20);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captchaMap.put(captchaId, new CaptchaItem(captcha.getCode(), System.currentTimeMillis() + CAPTCHA_EXPIRE_MILLIS));

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setImage("data:image/png;base64," + captcha.getImageBase64());
        return vo;
    }

    @Override
    public LoginResultVO login(LoginDTO dto, HttpServletRequest request) {
        String ip = getIp(request);
        checkIpLimit(ip);

        CaptchaItem item = captchaMap.remove(dto.getCaptchaId());
        if (item == null || item.expireAt < System.currentTimeMillis()) {
            throw new BusinessException("验证码已失效，请刷新后重试");
        }
        if (!item.code.equalsIgnoreCase(dto.getCaptchaCode())) {
            throw new BusinessException("验证码错误");
        }

        Long lockUntil = lockMap.get(dto.getUsername());
        if (lockUntil != null) {
            if (lockUntil > System.currentTimeMillis()) {
                throw new BusinessException("账号已锁定，请稍后再试");
            }
            lockMap.remove(dto.getUsername());
            failCountMap.remove(dto.getUsername());
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            recordFail(dto.getUsername());
            saveLoginLog(dto.getUsername(), ip, request.getHeader("User-Agent"), 0, "账号或密码错误");
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            saveLoginLog(dto.getUsername(), ip, request.getHeader("User-Agent"), 0, "账号已禁用");
            throw new BusinessException("账号已禁用");
        }

        failCountMap.remove(dto.getUsername());
        lockMap.remove(dto.getUsername());
        StpUtil.login(user.getId());
        saveLoginLog(dto.getUsername(), ip, request.getHeader("User-Agent"), 1, "登录成功");

        LoginResultVO result = new LoginResultVO();
        result.setToken(StpUtil.getTokenValue());
        result.setUser(toUserInfo(user));
        return result;
    }

    @Override
    public UserInfoVO info() {
        Long id = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserInfo(user);
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        Long id = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("新密码长度至少 8 位");
        }
        // 重新生成 BCrypt 密文并更新，避免明文落库
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(BCrypt.hashpw(newPassword));
        update.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
    }

    private void checkIpLimit(String ip) {
        IpWindow window = ipWindowMap.computeIfAbsent(ip, k -> new IpWindow());
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.resetAt > 60_000L) {
                window.resetAt = now;
                window.count = 0;
            }
            window.count++;
            if (window.count > MAX_IP_PER_MINUTE) {
                throw new BusinessException("操作过于频繁，请稍后再试");
            }
        }
    }

    private void recordFail(String username) {
        int count = failCountMap.merge(username, 1, Integer::sum);
        if (count >= MAX_FAIL) {
            lockMap.put(username, System.currentTimeMillis() + LOCK_MILLIS);
            failCountMap.remove(username);
        }
    }

    private void saveLoginLog(String username, String ip, String userAgent, int success, String message) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setSuccess(success);
        log.setMessage(message);
        log.setCreateTime(LocalDateTime.now());
        sysLoginLogMapper.insert(log);
    }

    private UserInfoVO toUserInfo(SysUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static class CaptchaItem {
        final String code;
        final long expireAt;

        CaptchaItem(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    private static class IpWindow {
        long resetAt = System.currentTimeMillis();
        int count = 0;
    }
}
