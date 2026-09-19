package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单步收入上限的测试。
 *
 * 这条规则的来由是实测：护送委托还没完成，AI 就在叙述里写"商队付了报酬"，
 * 顺手把整笔 80 金币（委托报酬）写进 coins_change —— 钱到账了，委托却还在进行中。
 * 现在非委托收入有上限，委托报酬由服务端在真正完成时单独发放。
 */
class SandboxEarnLimitTest {

    @Test
    void 超过上限就截断() {
        assertEquals(30, SandboxEarnLimit.clamp(80, 30));
        assertEquals(30, SandboxEarnLimit.clamp(31, 30));
    }

    @Test
    void 没超上限就原样放过() {
        assertEquals(12, SandboxEarnLimit.clamp(12, 30));
        assertEquals(30, SandboxEarnLimit.clamp(30, 30));
        assertEquals(0, SandboxEarnLimit.clamp(0, 30));
    }

    @Test
    void 没配置时用默认上限() {
        assertEquals(SandboxEarnLimit.DEFAULT_CAP, SandboxEarnLimit.maxEarn(0));
        assertEquals(SandboxEarnLimit.DEFAULT_CAP, SandboxEarnLimit.maxEarn(-5));
        assertEquals(SandboxEarnLimit.DEFAULT_CAP, SandboxEarnLimit.clamp(999, 0));
    }

    @Test
    void 管理员可以把上限调大调小() {
        assertEquals(5, SandboxEarnLimit.clamp(80, 5));
        assertEquals(200, SandboxEarnLimit.clamp(200, 500));
    }
}
