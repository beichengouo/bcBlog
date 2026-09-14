package com.bc.bcblog.service.impl;

import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.service.EmailCodeService;
import com.bc.bcblog.service.EmailService;
import com.bc.bcblog.vo.EmailCodeResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码服务。
 *
 * 并发安全说明：
 * 1. 验证码按邮箱（小写）分别存储，不同邮箱之间不会交叉校验；
 * 2. 同一邮箱发送有 60 秒冷却，避免快速重复发送覆盖验证码；
 * 3. 校验成功后原子移除，保证一个验证码只能使用一次；
 * 4. 单个验证码最多允许错误 5 次，超过后自动失效，防止暴力枚举。
 */
@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final long EXPIRE_MILLIS = 5 * 60 * 1000L;
    private static final long SEND_COOLDOWN_MILLIS = 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;
    /** 去掉容易混淆的 I、O、0、1 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CodeItem> codes = new ConcurrentHashMap<>();

    @Override
    public EmailCodeResultVO send(String email) {
        if (email == null || !email.contains("@")) {
            throw new BusinessException("邮箱格式不正确");
        }
        String normalized = email.trim().toLowerCase();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        String code = sb.toString();
        long now = System.currentTimeMillis();

        // compute 对同一个邮箱是原子操作，防止并发发送时互相覆盖
        codes.compute(normalized, (key, existing) -> {
            if (existing != null && now - existing.lastSendAt < SEND_COOLDOWN_MILLIS) {
                throw new BusinessException("验证码发送过于频繁，请稍后再试");
            }
            return new CodeItem(code, now + EXPIRE_MILLIS, now);
        });

        EmailCodeResultVO result = new EmailCodeResultVO();
        if (emailService.isConfigured()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("code", code);
            vars.put("email", email.trim());
            emailService.sendTemplate("register_code", email.trim(), vars);
            result.setSent(true);
            return result;
        }
        // 未配置邮件服务时，把验证码返回给前端用于测试
        result.setSent(false);
        result.setCode(code);
        return result;
    }

    @Override
    public void validate(String email, String code) {
        if (email == null || code == null || code.trim().isEmpty()) {
            throw new BusinessException("请输入邮箱验证码");
        }
        String key = email.trim().toLowerCase();
        CodeItem item = codes.get(key);
        if (item == null) {
            throw new BusinessException("验证码已失效，请重新获取");
        }
        // 对同一个验证码对象加锁，保证校验 + 移除是原子的，避免一个验证码被并发使用多次
        synchronized (item) {
            if (item.expireAt < System.currentTimeMillis()) {
                codes.remove(key, item);
                throw new BusinessException("验证码已失效，请重新获取");
            }
            if (item.attempts >= MAX_ATTEMPTS) {
                codes.remove(key, item);
                throw new BusinessException("验证码错误次数过多，请重新获取");
            }
            if (!item.code.equalsIgnoreCase(code.trim())) {
                item.attempts++;
                if (item.attempts >= MAX_ATTEMPTS) {
                    codes.remove(key, item);
                    throw new BusinessException("验证码错误次数过多，请重新获取");
                }
                throw new BusinessException("验证码错误");
            }
            // 只有仍然持有同一个验证码对象时才能移除成功，保证一次性使用
            if (!codes.remove(key, item)) {
                throw new BusinessException("验证码已使用，请重新获取");
            }
        }
    }

    private static class CodeItem {
        final String code;
        final long expireAt;
        final long lastSendAt;
        int attempts;

        CodeItem(String code, long expireAt, long lastSendAt) {
            this.code = code;
            this.expireAt = expireAt;
            this.lastSendAt = lastSendAt;
        }
    }
}
