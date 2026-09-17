package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 集市金币 → 积分折算测试。
 *
 * 重点锁住两条规则：
 *   1. 向上取整：1 金币不能算成 0 积分（否则用户白拿）；
 *   2. 汇率非法（0 / 负数）时按 1:1 处理，不能让折算炸掉或变成免费。
 */
class SandboxShopCoinTest {

    @Test
    @DisplayName("1 积分 = 1 金币：金币价就是积分数")
    void testOneToOne() {
        assertEquals(0, SandboxShopCoin.pointsOf(0, 1));
        assertEquals(1, SandboxShopCoin.pointsOf(1, 1));
        assertEquals(30, SandboxShopCoin.pointsOf(30, 1));
    }

    @Test
    @DisplayName("1 积分 = 10 金币：按汇率折算并向上取整")
    void testTenToOne() {
        assertEquals(0, SandboxShopCoin.pointsOf(0, 10));
        assertEquals(1, SandboxShopCoin.pointsOf(1, 10));
        assertEquals(1, SandboxShopCoin.pointsOf(10, 10));
        assertEquals(2, SandboxShopCoin.pointsOf(11, 10));
        assertEquals(3, SandboxShopCoin.pointsOf(30, 10));
        assertEquals(5, SandboxShopCoin.pointsOf(45, 10));
    }

    @Test
    @DisplayName("汇率异常时按 1:1 兜底，不允许出现 0 积分")
    void testBadRate() {
        assertEquals(7, SandboxShopCoin.pointsOf(7, 0));
        assertEquals(7, SandboxShopCoin.pointsOf(7, -3));
        assertEquals(0, SandboxShopCoin.pointsOf(-5, 10));
    }
}
