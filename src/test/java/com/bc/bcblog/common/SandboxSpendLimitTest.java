package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单次花费上限测试。
 *
 * 规则：余额分档与后台配置取较小值——钱包越薄越要省着花；
 * 后台把上限配成 0 / 负数时按默认 10 处理，且永远至少允许 1 金币。
 */
class SandboxSpendLimitTest {

    @Test
    @DisplayName("余额 < 20：最多 5 金币（哪怕后台配得很大）")
    void testPoor() {
        assertEquals(5, SandboxSpendLimit.maxSpend(0, 10));
        assertEquals(5, SandboxSpendLimit.maxSpend(12, 10));
        assertEquals(5, SandboxSpendLimit.maxSpend(19, 100));
    }

    @Test
    @DisplayName("余额 20~49：最多 8 金币")
    void testNormal() {
        assertEquals(8, SandboxSpendLimit.maxSpend(20, 10));
        assertEquals(8, SandboxSpendLimit.maxSpend(49, 100));
    }

    @Test
    @DisplayName("余额 ≥ 50：以后台配置为准")
    void testRich() {
        assertEquals(10, SandboxSpendLimit.maxSpend(50, 10));
        assertEquals(30, SandboxSpendLimit.maxSpend(200, 30));
        // 后台配得比分档还小，取更小的那个
        assertEquals(3, SandboxSpendLimit.maxSpend(200, 3));
    }

    @Test
    @DisplayName("配置非法时按默认 10，且至少允许花 1 金币")
    void testBadConfig() {
        assertEquals(10, SandboxSpendLimit.maxSpend(80, 0));
        assertEquals(8, SandboxSpendLimit.maxSpend(30, -5));
        assertEquals(1, SandboxSpendLimit.maxSpend(80, 1));
    }
}
