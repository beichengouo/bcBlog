package com.bc.bcblog.common;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三段式回复的解析测试。
 *
 * 重点：草稿/自审里也可能出现花括号，必须稳定取到 <final> 里的那份 JSON；
 * 而且 <draft>/<review> 绝不能漏到前台展示。
 */
class SandboxReplyParserTest {

    private static final String JSON = "{\"location\":\"晨雾森林\",\"actions\":[\"采药\"]}";

    @Test
    @DisplayName("忘了写 <final> 时，要取 </review> 之后的 JSON，而不是自审段里的示例对象")
    void 没有final标记时取最后一段之后的JSON() {
        String raw = "<recap>我在晨雾森林。</recap>\n"
                + "<think>想去采药。</think>\n"
                + "<draft>采三株银叶草。</draft>\n"
                + "<review>自查：地点 {\"location\":\"示例地点\"} 没问题，最终决定如下：</review>\n"
                + JSON;
        assertEquals(JSON, SandboxReplyParser.extractFinalJson(raw));
        JSONObject parsed = SandboxReplyParser.parse(raw);
        assertNotNull(parsed);
        assertEquals("晨雾森林", parsed.getStr("location"));
    }

    @Test
    @DisplayName("生成器忘了写 <final> 时，同样取最后一个分段之后的内容")
    void 生成器没有final标记() {
        String raw = "<think>x</think><draft>y</draft>\n[{\"name\":\"防雾斗篷\"}]";
        assertEquals("[{\"name\":\"防雾斗篷\"}]", SandboxReplyParser.extractFinalBlock(raw));
    }

    @Test
    @DisplayName("生成器：终稿是数组时也要能整段取出来")
    void 生成器终稿数组() {
        String raw = "<think>\n先想一下要摆什么货。\n</think>\n"
                + "<draft>\n1. 防雾斗篷 2. 银叶草\n</draft>\n"
                + "<final>\n[{\"name\":\"防雾斗篷\"},{\"name\":\"银叶草\"}]\n</final>";
        assertEquals("[{\"name\":\"防雾斗篷\"},{\"name\":\"银叶草\"}]",
                SandboxReplyParser.extractFinalBlock(raw));
    }

    @Test
    @DisplayName("生成器：终稿后面还跟了客套话时，仍只取 JSON")
    void 生成器终稿带尾巴() {
        String raw = "<think>想</think><draft>草稿</draft>\n<final>\n[{\"title\":\"甲\"}]\n</final>\n以上就是本批委托。";
        assertEquals("[{\"title\":\"甲\"}]", SandboxReplyParser.extractFinalBlock(raw));
    }

    @Test
    @DisplayName("生成器：关掉三段式（没有标记）时返回原文，保持老行为")
    void 生成器无标记() {
        assertEquals("[{\"name\":\"干粮\"}]", SandboxReplyParser.extractFinalBlock("  [{\"name\":\"干粮\"}]  "));
        assertNull(SandboxReplyParser.extractFinalBlock(null));
        assertNull(SandboxReplyParser.extractFinalBlock("   "));
    }

    @Test
    @DisplayName("生成器：代码块包裹的终稿要去掉围栏")
    void 生成器代码块() {
        String raw = "<think>x</think><draft>y</draft><final>\n```json\n[{\"name\":\"面包\"}]\n```\n</final>";
        assertEquals("[{\"name\":\"面包\"}]", SandboxReplyParser.extractFinalBlock(raw));
    }

    @Test
    @DisplayName("标准三段式：取 <final> 里的 JSON")
    void testFinalBlock() {
        String raw = "<draft>\n我打算让她去森林采药，顺便赚点钱（花 3 金币）。\n</draft>\n"
                + "<review>\n检查：位置在可用地点内；花费 3 与干粮相称；字段齐全。\n</review>\n"
                + "<final>\n" + JSON + "\n</final>";
        JSONObject obj = SandboxReplyParser.parse(raw);
        assertNotNull(obj);
        assertEquals("晨雾森林", obj.getStr("location"));
    }

    @Test
    @DisplayName("草稿里也有花括号时，仍然取 final 那份")
    void testDraftWithBraces() {
        String raw = "<draft>她看到一个牌子写着 {\"告示\"}，于是决定去采药。</draft>"
                + "<review>没问题</review><final>" + JSON + "</final>";
        JSONObject obj = SandboxReplyParser.parse(raw);
        assertNotNull(obj);
        assertEquals("晨雾森林", obj.getStr("location"));
    }

    @Test
    @DisplayName("没有标记时，取最后一段合法 JSON（模型偶尔不按格式给）")
    void testFallbackLastJson() {
        String raw = "先说说思路：{\"这不是\":\"最终结果\"}\n最终结果如下：\n" + JSON;
        JSONObject obj = SandboxReplyParser.parse(raw);
        assertNotNull(obj);
        assertEquals("晨雾森林", obj.getStr("location"));
    }

    @Test
    @DisplayName("被 ``` 围栏包住也能解析")
    void testCodeFence() {
        String raw = "<final>\n```json\n" + JSON + "\n```\n</final>";
        assertNotNull(SandboxReplyParser.parse(raw));
    }

    @Test
    @DisplayName("回归：嵌套对象 + 没有 <final> 标记时，必须取最外层那份（不能取到内层的 {\"delta\":-1}）")
    void testNestedObjectRegression() {
        // 这段就是线上真实踩坑的回复：草稿/自审后直接跟 JSON，且 JSON 里有嵌套对象
        String raw = "<draft>\n伊露雅在酒馆里，金币只有 3 枚，打算攒路费。\n</draft>\n"
                + "<review>\n- 地点：圣云教国，合理。\n- 金币：要省钱。\n- 修正：写出准备出发。\n</review>\n\n"
                + "{\"next_after_minutes\":580,\"next_after_reason\":\"深度睡眠\",\"location\":\"圣云教国\","
                + "\"sub_location\":\"酒馆一楼的喧闹吧台\",\"x\":43,\"y\":35,"
                + "\"actions\":[\"打听明天的商队\",\"搬运麦酒换搭车机会\"],\"inner_voice\":\"风还在吹。\","
                + "\"status\":{\"体力\":95,\"魔力\":92,\"饥饿度\":0,\"心情\":\"充满干劲\"},"
                + "\"goal\":\"前往冒险家协会\",\"coins_change\":2,\"combat_change\":0,\"companions\":[],"
                + "\"favor_changes\":{},\"items_change\":{\"晨雾森林的野浆果\":{\"delta\":-1}},"
                + "\"shop_buy\":[],\"news_refs\":[],\"summary\":\"在酒馆打工并联系好了明早的顺风车。\"}";
        JSONObject obj = SandboxReplyParser.parse(raw);
        assertNotNull(obj);
        // 关键断言：拿到的是完整对象，而不是内层的 {"delta":-1}
        assertEquals("圣云教国", obj.getStr("location"));
        assertEquals("前往冒险家协会", obj.getStr("goal"));
        assertEquals(2, obj.getInt("coins_change").intValue());
        assertTrue(obj.getJSONArray("actions").size() == 2);
    }

    @Test
    @DisplayName("整份回复就是纯 JSON（旧格式）依然可用")
    void testPlainJson() {
        assertNotNull(SandboxReplyParser.parse(JSON));
    }

    @Test
    @DisplayName("完全没有 JSON 时返回 null（交给补救流程）")
    void testNoJson() {
        assertNull(SandboxReplyParser.parse("<draft>只有草稿，没来得及给结果</draft>"));
        assertNull(SandboxReplyParser.parse(""));
        assertNull(SandboxReplyParser.parse(null));
    }

    @Test
    @DisplayName("前台兜底文本里不能出现 draft/review/final 标记")
    void testDisplayText() {
        String raw = "<draft>草稿内容</draft><review>自审内容</review><final>{\"location\":\"晨雾森林\"}</final>";
        String text = SandboxReplyParser.displayText(raw);
        assertTrue(!text.contains("draft") && !text.contains("review") && !text.contains("final"));
        assertTrue(text.contains("晨雾森林"));

        // 只有草稿时：把草稿整段去掉，不留标记
        String onlyDraft = SandboxReplyParser.displayText("<draft>只是草稿</draft>");
        assertTrue(!onlyDraft.contains("draft"));
    }

    @Test
    @DisplayName("思考阶段：四段式能解析，且 <think> 也不会漏到前台")
    void testThinkStage() {
        String raw = "<think>处境：金币只有 3 枚。可选做法：A 走路上路 14 小时；B 搭商队顺风车；C 先打工。"
                + "选 B+C：先打听顺风车，同时打点零工攒钱。代价：今晚不能赶路。</think>\n"
                + "<draft>在酒馆打工、问明天的商队。</draft>\n"
                + "<review>地点与间隔都合理，物品只有吃掉浆果 -1。</review>\n"
                + "<final>" + JSON + "</final>";
        JSONObject obj = SandboxReplyParser.parse(raw);
        assertNotNull(obj);
        assertEquals("晨雾森林", obj.getStr("location"));

        // 解析失败时的前台兜底文本里，thinking 内容与标记都要清掉
        String text = SandboxReplyParser.displayText("<think>内部权衡过程</think><draft>草稿</draft>");
        assertTrue(!text.contains("think") && !text.contains("内部权衡过程"));
    }
}
