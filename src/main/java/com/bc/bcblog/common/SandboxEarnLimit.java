package com.bc.bcblog.common;

/**
 * 沙盒「单次行动的收入上限」。
 *
 * 背景（实测）：支出一直有上限（sandbox_max_spend_per_act，默认 10 金币），
 * 但**收入完全没有限制**，于是 AI 可以在一句话里让角色进账一大笔——
 * 最典型的一次是护送委托还没完成，它就在 actions 里写"商队付了报酬"，
 * 顺手把 80 金币（整笔委托报酬）写进 coins_change，导致"钱到账了但委托还在进行中"。
 *
 * 这里只给**非委托**的收入设上限（打工、摆摊、卖采集物、捡到东西这类由 AI 自由发挥的收入）；
 * 委托报酬由服务端在真正完成时统一发放（payQuestCoins），不经过这个额度。
 */
public final class SandboxEarnLimit {

    /** 后台没配置时用的默认上限 */
    public static final int DEFAULT_CAP = 30;

    private SandboxEarnLimit() {
    }

    /**
     * 单次非委托收入的上限。
     *
     * @param configured 后台配置的上限（sandbox_max_earn_per_act）；&lt;= 0 表示用默认值
     */
    public static int maxEarn(int configured) {
        return configured <= 0 ? DEFAULT_CAP : configured;
    }

    /**
     * 把这一步的收入夹到上限内。
     *
     * @param change  AI 给的收入（只处理正数）
     * @param configured 后台配置的上限
     * @return 夹过之后的收入
     */
    public static int clamp(int change, int configured) {
        int cap = maxEarn(configured);
        return change <= cap ? change : cap;
    }
}
