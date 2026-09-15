package com.bc.bcblog.service;

/** 安全密码服务：敏感操作二次验证（本次登录内验证一次即可）。 */
public interface SecurityPasswordService {

    /** 是否已设置安全密码 */
    boolean hasPassword(Long userId);

    /** 校验安全密码；未设置过安全密码时回退用登录密码校验 */
    boolean verify(Long userId, String password);

    /** 设置/修改安全密码（需要先用登录密码确认身份） */
    void setPassword(Long userId, String loginPassword, String newSecurityPassword);

    /** 当前会话是否已完成二次验证 */
    boolean isVerified();

    /** 标记当前会话已完成二次验证（有效期 = 本次登录） */
    void markVerified();
}
