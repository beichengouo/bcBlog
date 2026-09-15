package com.bc.bcblog.service;

import com.bc.bcblog.entity.SysUser;

/** 登录安全服务：异常登录检测与邮件提醒。 */
public interface LoginSecurityService {

    /**
     * 登录成功后调用：判断是否异常（新 IP / IP 变化 / 深夜时段），异常时发提醒邮件。
     * 不阻断登录。
     */
    void afterLoginSuccess(SysUser user, String ip, String userAgent);

    /** 登录失败时调用：连续失败达到阈值时发提醒邮件 */
    void afterLoginFail(SysUser user, String ip, String userAgent, String reason);
}
