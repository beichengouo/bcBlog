package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 旅人委托的两条规则测试。
 *
 * 这两条一旦写错不会报错，只会让玩法崩掉：
 *   · 进度不设单步上限 → AI 一步从 0 写到 100，委托变成"一句话完成"；
 *   · 进度允许回落 → 角色下一次行动随便给个数字，进度就倒退回去；
 *   · 战力不夹区间 → 凶险之地出现 3 战斗力的野狗，或安全地带出现 200 战力的魔龙。
 */
class SandboxQuestRuleTest {

    @Test
    void 进度可以做一步到位的封顶() {
        // AI 直接写 100，也只能涨 stepMax
        assertEquals(40, SandboxQuestRule.apply(0, 100, 40));
        assertEquals(100, SandboxQuestRule.apply(60, 100, 40));
        // 正常推进
        assertEquals(30, SandboxQuestRule.apply(10, 30, 40));
        // 超过 100 要夹住
        assertEquals(100, SandboxQuestRule.apply(90, 100, 40));
    }

    @Test
    void 进度不允许回落() {
        // AI 写了更小的值 → 当作没推进
        assertEquals(70, SandboxQuestRule.apply(70, 20, 40));
        // 没给或给了负数 → 当作没推进
        assertEquals(70, SandboxQuestRule.apply(70, null, 40));
        assertEquals(70, SandboxQuestRule.apply(70, -1, 40));
    }

    @Test
    void 进度入参越界也要兜住() {
        assertEquals(0, SandboxQuestRule.apply(-50, null, 40));
        assertEquals(100, SandboxQuestRule.apply(200, null, 40));
        // stepMax 被填成 0 或负数时至少给 1，不会卡死在原地
        assertEquals(1, SandboxQuestRule.apply(0, 50, 0));
    }

    @Test
    void 对手战力夹到地点区间里() {
        // 区间内原样返回
        assertEquals(22, SandboxQuestRule.clampPower(22, 15, 30).intValue());
        // 低于下限 / 高于上限
        assertEquals(15, SandboxQuestRule.clampPower(3, 15, 30).intValue());
        assertEquals(30, SandboxQuestRule.clampPower(200, 15, 30).intValue());
        // 只配了一边也要能用
        assertEquals(15, SandboxQuestRule.clampPower(3, 15, 0).intValue());
        assertEquals(30, SandboxQuestRule.clampPower(200, 0, 30).intValue());
    }

    @Test
    void 没设区间或没给战力时不改动() {
        assertEquals(50, SandboxQuestRule.clampPower(50, 0, 0).intValue());
        assertNull(SandboxQuestRule.clampPower(null, 15, 30));
        assertEquals(0, SandboxQuestRule.clampPower(0, 15, 30).intValue());
    }

    @Test
    void 单步上限按难度分档() {
        String config = "100,70,50,35,20";
        // 难度 1 允许一步做完（「清点库房」这类杂活不该被硬拖两天）
        assertEquals(100, SandboxQuestRule.stepMaxByDifficulty(config, 1, 55));
        assertEquals(70, SandboxQuestRule.stepMaxByDifficulty(config, 2, 55));
        assertEquals(50, SandboxQuestRule.stepMaxByDifficulty(config, 3, 55));
        assertEquals(35, SandboxQuestRule.stepMaxByDifficulty(config, 4, 55));
        assertEquals(20, SandboxQuestRule.stepMaxByDifficulty(config, 5, 55));
    }

    @Test
    void 难度越界时夹到已有档位() {
        String config = "100,70,50,35,20";
        assertEquals(100, SandboxQuestRule.stepMaxByDifficulty(config, 0, 55));
        assertEquals(100, SandboxQuestRule.stepMaxByDifficulty(config, -3, 55));
        assertEquals(20, SandboxQuestRule.stepMaxByDifficulty(config, 9, 55));
        // 只配了两档时，后面的难度沿用最后一档，不会取到越界
        assertEquals(70, SandboxQuestRule.stepMaxByDifficulty("100,70", 5, 55));
    }

    @Test
    void 分档配置没配或写坏时用兜底值() {
        assertEquals(55, SandboxQuestRule.stepMaxByDifficulty(null, 3, 55));
        assertEquals(55, SandboxQuestRule.stepMaxByDifficulty("   ", 3, 55));
        assertEquals(55, SandboxQuestRule.stepMaxByDifficulty(",", 3, 55));
        // 单个数字写坏 → 这一档用兜底值，其它档不受影响
        assertEquals(100, SandboxQuestRule.stepMaxByDifficulty("100,abc,50", 1, 55));
        assertEquals(55, SandboxQuestRule.stepMaxByDifficulty("100,abc,50", 2, 55));
        assertEquals(50, SandboxQuestRule.stepMaxByDifficulty("100,abc,50", 3, 55));
    }

    @Test
    void 分档数值本身也会被夹到1到100() {
        assertEquals(100, SandboxQuestRule.stepMaxByDifficulty("999,70", 1, 55));
        assertEquals(1, SandboxQuestRule.stepMaxByDifficulty("0,70", 1, 55));
        assertEquals(1, SandboxQuestRule.stepMaxByDifficulty("-20,70", 1, 55));
    }
}
