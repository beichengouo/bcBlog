package com.bc.bcblog.component;

/**
 * 审计上下文：调用 AI 之前由业务代码设置「这次调用算什么动作」，
 * 中心化的 AI 调用处再据此写审计日志，避免每个接口都手写一遍。
 */
public final class AuditContext {

    private static final ThreadLocal<String> ACTION = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SCHEDULE = new ThreadLocal<>();

    private AuditContext() {
    }

    /** 管理员手动触发 */
    public static void manual(String action) {
        ACTION.set(action);
        SCHEDULE.set(Boolean.FALSE);
    }

    /** 定时任务触发 */
    public static void schedule(String action) {
        ACTION.set(action);
        SCHEDULE.set(Boolean.TRUE);
    }

    public static String action() {
        String action = ACTION.get();
        return action == null || action.isEmpty() ? "AI 调用" : action;
    }

    public static boolean isSchedule() {
        return Boolean.TRUE.equals(SCHEDULE.get());
    }

    /** 是否已经设置过动作（避免被内层调用覆盖） */
    public static boolean isSet() {
        return ACTION.get() != null;
    }

    public static void clear() {
        ACTION.remove();
        SCHEDULE.remove();
    }
}
