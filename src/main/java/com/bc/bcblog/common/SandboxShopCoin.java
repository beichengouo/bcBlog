package com.bc.bcblog.common;

/**
 * 旅人集市的金币 ↔ 积分换算。
 *
 * 集市里商品用金币标价（沙盒角色的钱包是金币），前台用户掏的是积分，
 * 所以购买时要按汇率折算：points = ceil(金币价 / 汇率)。
 * 向上取整是硬性规则——不能让 1 金币的商品变成 0 积分（那样等于白送）。
 * 前端「集市管理」和前台集市弹窗都按同一公式显示，改这里记得一起改。
 */
public final class SandboxShopCoin {

    private SandboxShopCoin() {
    }

    /** 金币价折算成积分（汇率非法时按 1:1 处理） */
    public static int pointsOf(int coinPrice, int rate) {
        if (coinPrice <= 0) {
            return 0;
        }
        int safeRate = Math.max(1, rate);
        return (coinPrice + safeRate - 1) / safeRate;
    }
}
