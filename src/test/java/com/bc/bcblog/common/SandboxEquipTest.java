package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备栏纯规则的单测：槽位识别、加成夹取、能不能穿。
 * 这些规则写歪了不会报错，只会让战力系统悄悄失真，所以在这里钉死。
 */
class SandboxEquipTest {

    @Test
    void 槽位规范化() {
        assertEquals("weapon", SandboxEquip.normalizeSlot("weapon"));
        assertEquals("weapon", SandboxEquip.normalizeSlot(" WEAPON "));
        assertEquals("armor", SandboxEquip.normalizeSlot("护具"));
        assertEquals("none", SandboxEquip.normalizeSlot("随便写的"));
        assertEquals("none", SandboxEquip.normalizeSlot(null));
        assertTrue(SandboxEquip.wearable("accessory"));
        assertFalse(SandboxEquip.wearable("none"));
    }

    @Test
    void 按名字猜槽位() {
        assertEquals("weapon", SandboxEquip.guessSlot("精钢长剑"));
        assertEquals("weapon", SandboxEquip.guessSlot("黑檀法杖"));
        assertEquals("offhand", SandboxEquip.guessSlot("翻新的制式铁盾"));
        assertEquals("armor", SandboxEquip.guessSlot("略显破旧的深色防风斗篷"));
        assertEquals("armor", SandboxEquip.guessSlot("圣殿骑士胸甲"));
        assertEquals("accessory", SandboxEquip.guessSlot("薰衣草戒指"));
        assertEquals("accessory", SandboxEquip.guessSlot("月长石护符"));
        // 吃的、材料不能被当成装备
        assertEquals("none", SandboxEquip.guessSlot("荧光蘑菇干"));
        assertEquals("none", SandboxEquip.guessSlot("硬如石头的饼干"));
        assertEquals("none", SandboxEquip.guessSlot("银叶草"));
        // 带「水」字的武器不该被材料词误伤
        assertEquals("weapon", SandboxEquip.guessSlot("水纹长剑"));
        assertEquals("none", SandboxEquip.guessSlot("面包"));
    }

    @Test
    void 加成区间解析与夹取() {
        String cfg = "1-10,10-20,20-30,30-60,60-120";
        assertEquals(1, SandboxEquip.bonusRange(cfg, 1)[0]);
        assertEquals(10, SandboxEquip.bonusRange(cfg, 1)[1]);
        assertEquals(60, SandboxEquip.bonusRange(cfg, 5)[0]);
        assertEquals(120, SandboxEquip.bonusRange(cfg, 5)[1]);

        // 落在区间里原样保留，超出上限压下来
        assertEquals(3, SandboxEquip.clampBonus(cfg, 1, 3));
        assertEquals(10, SandboxEquip.clampBonus(cfg, 1, 99));
        assertEquals(45, SandboxEquip.clampBonus(cfg, 4, 45));
        assertEquals(120, SandboxEquip.clampBonus(cfg, 5, 999));
        // 没给数值时取区间中点（历史物品兜底）
        assertEquals(5, SandboxEquip.clampBonus(cfg, 1, null));
        assertEquals(90, SandboxEquip.clampBonus(cfg, 5, 0));

        // 管理员改成别的区间后立刻生效；写坏的那一档退回默认
        String custom = "5-8,30-40";
        assertEquals(5, SandboxEquip.bonusRange(custom, 1)[0]);
        assertEquals(40, SandboxEquip.bonusRange(custom, 2)[1]);
        assertEquals(20, SandboxEquip.bonusRange(custom, 3)[0]);
        assertEquals("30-60", SandboxEquip.bonusRange("坏掉的,配置", 4)[0]
                + "-" + SandboxEquip.bonusRange("坏掉的,配置", 4)[1]);
    }

    @Test
    void 加成不能超过自身实力() {
        assertTrue(SandboxEquip.canWear(10, 10));
        assertTrue(SandboxEquip.canWear(9, 10));
        assertFalse(SandboxEquip.canWear(11, 10));
        // 自身实力再低也至少按 1 算，避免除零/全禁
        assertFalse(SandboxEquip.canWear(2, 0));
        assertTrue(SandboxEquip.canWear(1, 0));
    }

    @Test
    void 破损改名与修复() {
        assertEquals("破损的精钢长剑", SandboxEquip.brokenName("精钢长剑"));
        assertEquals("破损的精钢长剑", SandboxEquip.brokenName("破损的精钢长剑"));
        assertEquals("精钢长剑", SandboxEquip.repairedName("破损的精钢长剑"));
        assertEquals("精钢长剑", SandboxEquip.repairedName("精钢长剑"));
        assertTrue(SandboxEquip.looksBroken("破损的铜盾"));
        assertFalse(SandboxEquip.looksBroken("铜盾"));
    }
}
