package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙盒失败退避的计算测试。
 * 目标：连续失败时退避逐步拉长、到上限封顶，避免模型挂掉后每 5 分钟重试一次烧额度。
 */
class SandboxBackoffTest {

    @Test
    @DisplayName("默认参数下退避按 2 倍递增，并在上限封顶")
    void testEscalating() {
        assertEquals(15, SandboxBackoff.minutes(1));
        assertEquals(30, SandboxBackoff.minutes(2));
        assertEquals(60, SandboxBackoff.minutes(3));
        assertEquals(120, SandboxBackoff.minutes(4));
        // 上限 120，之后一直保持
        assertEquals(120, SandboxBackoff.minutes(5));
        assertEquals(120, SandboxBackoff.minutes(20));
    }

    @Test
    @DisplayName("起步值与上限可自定义，非法值回退到默认")
    void testCustomAndInvalid() {
        assertEquals(5, SandboxBackoff.minutes(1, 5, 40));
        assertEquals(10, SandboxBackoff.minutes(2, 5, 40));
        assertEquals(20, SandboxBackoff.minutes(3, 5, 40));
        assertEquals(40, SandboxBackoff.minutes(4, 5, 40));

        // 起步值非法 → 用默认 15；上限小于起步值 → 至少按默认 120 兜底
        assertEquals(15, SandboxBackoff.minutes(1, 0, 0));
        assertEquals(120, SandboxBackoff.minutes(5, 0, 0));
        // 次数非法（0 或负数）按第 1 次处理
        assertEquals(15, SandboxBackoff.minutes(0));
        assertEquals(15, SandboxBackoff.minutes(-3));
    }

    @Test
    @DisplayName("极端次数不会因为位移溢出成负数")
    void testNoOverflow() {
        int minutes = SandboxBackoff.minutes(1000);
        assertTrue(minutes > 0, "退避分钟数必须为正数");
        assertEquals(120, minutes);
    }

    @Test
    @DisplayName("失败原因带上连续次数与退避时长，方便后台一眼看出")
    void testDescribeError() {
        String text = SandboxBackoff.describeError(3, 60, "上游返回 401");
        assertTrue(text.contains("连续失败 3 次"));
        assertTrue(text.contains("退避 60 分钟"));
        assertTrue(text.contains("上游返回 401"));
        assertEquals("（连续失败 1 次，已退避 15 分钟）", SandboxBackoff.describeError(1, 15, null));
    }
}
