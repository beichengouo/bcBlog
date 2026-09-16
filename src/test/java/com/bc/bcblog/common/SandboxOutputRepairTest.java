package com.bc.bcblog.common;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 掉格式补全的字段级合并测试。
 * 重点是「补全不能改写剧情」：原文里已有的内容必须一个字都不变。
 */
class SandboxOutputRepairTest {

    /** AI 掉格式的典型输出：只有剧情字段，缺 next_after_* 与 status */
    private JSONObject brokenOutput() {
        return JSONUtil.parseObj("{"
                + "\"location\":\"晨雾森林\","
                + "\"sub_location\":\"长满萤石的树洞\","
                + "\"x\":42,\"y\":64,"
                + "\"actions\":[\"蹲下查看地上的爪印\",\"把斗篷裹紧了一点\"],"
                + "\"inner_voice\":\"这里安静得有点过分。\","
                + "\"coins_change\":0,"
                + "\"summary\":\"在森林里查看爪印\""
                + "}");
    }

    /** 模型"借补全之名"重写了一遍剧情，同时还补上了缺失字段 */
    private JSONObject rewrittenRepair() {
        return JSONUtil.parseObj("{"
                + "\"next_after_minutes\":45,"
                + "\"next_after_reason\":\"查看痕迹\","
                + "\"location\":\"圣云教国\","
                + "\"sub_location\":\"圣光塔三层\","
                + "\"x\":45,\"y\":28,"
                + "\"actions\":[\"完全不同的动作\"],"
                + "\"inner_voice\":\"完全不同的心里话。\","
                + "\"status\":{\"体力\":70,\"魔力\":50,\"饥饿度\":40,\"心情\":\"警惕\"},"
                + "\"summary\":\"完全不同的总结\""
                + "}");
    }

    @Test
    @DisplayName("只补缺失字段，剧情内容一个字都不被改写")
    void testMergeKeepsOriginalStory() {
        JSONObject merged = SandboxOutputRepair.merge(brokenOutput(), rewrittenRepair());

        // 补上的字段
        assertEquals(45, merged.getInt("next_after_minutes"));
        assertEquals("查看痕迹", merged.getStr("next_after_reason"));
        assertFalse(merged.getJSONObject("status").isEmpty());

        // 原有内容必须原样保留（这是防改写的关键）
        assertEquals("晨雾森林", merged.getStr("location"));
        assertEquals("长满萤石的树洞", merged.getStr("sub_location"));
        assertEquals(42, merged.getInt("x"));
        assertEquals(64, merged.getInt("y"));
        assertEquals("这里安静得有点过分。", merged.getStr("inner_voice"));
        assertEquals("在森林里查看爪印", merged.getStr("summary"));
        assertEquals(2, merged.getJSONArray("actions").size());
        assertEquals("蹲下查看地上的爪印", merged.getJSONArray("actions").getStr(0));
    }

    @Test
    @DisplayName("非法值也算缺失：填 0 的间隔会被补全值替换")
    void testMergeReplacesInvalidValue() {
        JSONObject broken = JSONUtil.parseObj("{\"next_after_minutes\":0,\"next_after_reason\":\"\"}");
        JSONObject fixed = JSONUtil.parseObj("{\"next_after_minutes\":360,\"next_after_reason\":\"过夜休息\"}");
        JSONObject merged = SandboxOutputRepair.merge(broken, fixed);
        assertEquals(360, merged.getInt("next_after_minutes"));
        assertEquals("过夜休息", merged.getStr("next_after_reason"));
    }

    @Test
    @DisplayName("空数组/空对象是有意义的，不该被当成缺失覆盖掉")
    void testEmptyCollectionsAreMeaningful() {
        JSONObject broken = JSONUtil.parseObj(
                "{\"companions\":[],\"favor_changes\":{},\"items_change\":{},\"news_refs\":[],\"coins_change\":0,\"x\":0,\"y\":0}");
        assertFalse(SandboxOutputRepair.needRepair("companions", new JSONArray()));
        assertFalse(SandboxOutputRepair.needRepair("favor_changes", new JSONObject()));
        assertFalse(SandboxOutputRepair.needRepair("items_change", new JSONObject()));
        assertFalse(SandboxOutputRepair.needRepair("news_refs", new JSONArray()));
        assertFalse(SandboxOutputRepair.needRepair("coins_change", 0));
        assertFalse(SandboxOutputRepair.needRepair("x", 0));
        assertFalse(SandboxOutputRepair.needRepair("y", 0));
        // 整个字段缺失时才补
        assertTrue(SandboxOutputRepair.needRepair("companions", null));
        assertTrue(SandboxOutputRepair.needRepair("x", null));
        // status 是例外：空对象等于没给状态
        assertTrue(SandboxOutputRepair.needRepair("status", new JSONObject()));
        assertTrue(SandboxOutputRepair.needRepair("actions", new JSONArray()));
        // 补全结果没有这个字段时，也不该把原文清空
        JSONObject merged = SandboxOutputRepair.merge(broken, JSONUtil.parseObj("{}"));
        assertEquals(0, merged.getJSONArray("companions").size());
        assertEquals(0, merged.getInt("coins_change"));
    }

    @Test
    @DisplayName("原文不是 JSON 时整份采用补全结果；输出太短则判定为不可补全")
    void testFallbackAndUsableContent() {
        JSONObject fix = JSONUtil.parseObj("{\"location\":\"圣云教国\"}");
        assertEquals(fix, SandboxOutputRepair.merge(null, fix), "没有原文可用时直接采用补全结果");
        JSONObject base = JSONUtil.parseObj("{\"location\":\"圣云教国\"}");
        assertEquals(base, SandboxOutputRepair.merge(base, null), "补全失败时保留原文");

        assertFalse(SandboxOutputRepair.hasUsableContent(null));
        assertFalse(SandboxOutputRepair.hasUsableContent("   "));
        assertFalse(SandboxOutputRepair.hasUsableContent("抱歉，我不能"));
        assertTrue(SandboxOutputRepair.hasUsableContent("{\"location\":\"晨雾森林\",\"x\":42,\"y\":64}"));
    }

    @Test
    @DisplayName("items_change 兼容新旧两种写法，并能取出物品描述")
    void testItemFormat() {
        // 旧写法：只有数量
        assertEquals(2, SandboxOutputRepair.itemDelta(2));
        assertEquals(-1, SandboxOutputRepair.itemDelta(-1));
        assertNull(SandboxOutputRepair.itemDescription(2));

        // 新写法：{delta, description}
        JSONObject item = JSONUtil.parseObj("{\"delta\":1,\"description\":\"能入药的淡紫色小草\"}");
        assertEquals(1, SandboxOutputRepair.itemDelta(item));
        assertEquals("能入药的淡紫色小草", SandboxOutputRepair.itemDescription(item));

        // 兼容 count / quantity / desc 这些别名
        assertEquals(3, SandboxOutputRepair.itemDelta(JSONUtil.parseObj("{\"count\":3}")));
        assertEquals(2, SandboxOutputRepair.itemDelta(JSONUtil.parseObj("{\"quantity\":2}")));
        assertEquals("旧地图", SandboxOutputRepair.itemDescription(JSONUtil.parseObj("{\"desc\":\"旧地图\"}")));

        // 空描述视同没有描述（会触发补全）
        assertNull(SandboxOutputRepair.itemDescription(JSONUtil.parseObj("{\"delta\":1,\"description\":\"  \"}")));
    }
}
