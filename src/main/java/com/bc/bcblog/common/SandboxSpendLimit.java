package com.bc.bcblog.common;

/**
 * 沙盒「单次行动的花费上限」。
 *
 * 背景：AI 会随手写一个 coins_change 负数（例如第一次行动就写 -15，而它只买了浆果和火把），
 * 服务端过去只校验"不超过余额"，于是平白扣掉一大笔钱。
 * 现在按「余额分档 + 后台配置上限」取较小值：钱包越薄越要省着花。
 *
 * 注意：这里只管**非集市消费**（吃饭、住店、车马、情报、打点这类由 AI 自由发挥的支出）；
 * 旅人集市的购买走 shop_buy，有真实标价，不占用这个额度。
 */
public final class SandboxSpendLimit {

    /** 钱包很薄（<20 金币）时，单次最多花这么多 */
    public static final int TIER_POOR = 5;
    /** 中等（20~49 金币）时，单次最多花这么多 */
    public static final int TIER_NORMAL = 8;

    private SandboxSpendLimit() {
    }

    /**
     * 单次非集市花费的上限。
     *
     * @param coinsBefore 这一步开始前身上的金币
     * @param configured  后台配置的上限（sandbox_max_spend_per_act）
     */
    public static int maxSpend(int coinsBefore, int configured) {
        int cap = configured <= 0 ? 10 : configured;
        int tier;
        if (coinsBefore < 20) {
            tier = TIER_POOR;
        } else if (coinsBefore < 50) {
            tier = TIER_NORMAL;
        } else {
            tier = cap;
        }
        int limit = Math.min(cap, tier);
        return Math.max(1, limit);
    }
}
