package com.bc.bcblog.common;

/**
 * 角色正在执行中（并发保护）。
 *
 * 用途：同一个角色的两个行动同时跑，会互相覆盖金币、也会白烧 token。
 * 抢锁失败时抛这个异常：
 *   · 管理员手动点「立即执行」→ 直接看到"正在执行中，请稍候"；
 *   · 定时任务 / 全员行动 / 回应链 → 跳过这个角色，**不计入失败、不触发失败退避**。
 */
public class SandboxBusyException extends BusinessException {

    public SandboxBusyException(String message) {
        // 409：冲突（资源正忙），前端会直接弹消息，不会被当成登录失效
        super(409, message);
    }
}
