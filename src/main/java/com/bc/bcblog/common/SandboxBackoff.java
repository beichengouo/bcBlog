package com.bc.bcblog.common;

/**
 * 沙盒 AI 调用失败后的退避计算。
 *
 * 为什么需要：沙盒每 5 分钟扫一次「已到期」的角色。以前失败时不改 next_run_time，
 * 角色就一直处于逾期状态，于是每 5 分钟重试一次；又因为失败不产生行动记录、
 * 绕过了每日上限，模型一旦挂掉就会持续消耗额度。
 *
 * 现在失败会把 next_run_time 往后推：第 1 次失败退避 base 分钟，之后每失败一次翻倍，
 * 到 max 分钟封顶；成功一次就清零。管理员手动执行不受影响。
 */
public final class SandboxBackoff {

    /** 退避起步值（分钟） */
    public static final int DEFAULT_BASE_MINUTES = 15;
    /** 退避上限（分钟） */
    public static final int DEFAULT_MAX_MINUTES = 120;
    /** 连续失败次数上限（防止 2 的幂次溢出，超过后一直按上限算） */
    private static final int MAX_FAIL_COUNT = 30;

    private SandboxBackoff() {
    }

    /**
     * 计算退避分钟数。
     *
     * @param failCount 连续失败次数（从 1 开始；传 0 也按 1 次处理）
     * @param base      起步分钟数，非法值用默认值
     * @param max       上限分钟数，非法值用默认值
     */
    public static int minutes(int failCount, int base, int max) {
        int safeBase = base > 0 ? base : DEFAULT_BASE_MINUTES;
        int safeMax = max >= safeBase ? max : Math.max(safeBase, DEFAULT_MAX_MINUTES);
        int count = Math.max(1, Math.min(MAX_FAIL_COUNT, failCount));
        long value = (long) safeBase * (1L << (count - 1));
        return (int) Math.min(safeMax, value);
    }

    /** 用默认参数计算退避分钟数 */
    public static int minutes(int failCount) {
        return minutes(failCount, DEFAULT_BASE_MINUTES, DEFAULT_MAX_MINUTES);
    }

    /** 失败原因加上「连续失败 N 次」的前缀，方便后台一眼看出是被退避了 */
    public static String describeError(int failCount, int minutes, String message) {
        return "（连续失败 " + failCount + " 次，已退避 " + minutes + " 分钟）"
                + (message == null ? "" : message);
    }
}
