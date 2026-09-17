package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 物品名规范化测试。
 *
 * 目标：把模型中英混排的连接词改回中文，同时**绝不碰**真正的英文名字（HK416 之类）。
 */
class SandboxItemNameTest {

    @Test
    @DisplayName("实测案例：晨雾森林 of 野浆果 → 晨雾森林的野浆果")
    void testRealCase() {
        assertEquals("晨雾森林的野浆果", SandboxItemName.normalize("晨雾森林 of 野浆果"));
        assertEquals("晨雾森林的野浆果", SandboxItemName.normalize("晨雾森林of野浆果"));
    }

    @Test
    @DisplayName("和 / 去掉 the，并清掉中文字之间的空格")
    void testAndThe() {
        assertEquals("面包和奶酪", SandboxItemName.normalize("面包 and 奶酪"));
        assertEquals("旧地图", SandboxItemName.normalize("the 旧地图"));
        assertEquals("晨雾森林的野浆果", SandboxItemName.normalize("晨雾森林 的 野浆果"));
    }

    @Test
    @DisplayName("纯中文名字原样返回")
    void testChineseOnly() {
        assertEquals("翡翠树脂火把", SandboxItemName.normalize("翡翠树脂火把"));
        assertEquals("", SandboxItemName.normalize("   "));
        assertEquals("", SandboxItemName.normalize(null));
    }

    @Test
    @DisplayName("真正的英文名字保留（不误伤）")
    void testKeepRealEnglish() {
        assertEquals("HK416", SandboxItemName.normalize("HK416"));
        assertEquals("MP3 播放器", SandboxItemName.normalize("MP3 播放器"));
        assertEquals("P90 冲锋枪", SandboxItemName.normalize("P90 冲锋枪"));
    }
}
