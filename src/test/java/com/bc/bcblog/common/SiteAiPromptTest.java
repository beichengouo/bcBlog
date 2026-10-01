package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯文本用途（评论 / 回复 / 状态 / 记忆）的输出清洗。
 *
 * 背景：服务器上实测出现过读者看到的评论是 {"reply": "你好。…"} 整段 JSON——
 * 模型明明被要求"只输出正文"，还是套了一层 JSON 信封，以前原文直接进了评论。
 */
class SiteAiPromptTest {

    @Test
    void 纯文本原样返回() {
        assertEquals("你好，正在执行。", SiteAiPrompt.plainText("你好，正在执行。", "reply"));
    }

    @Test
    void 拆掉json信封() {
        String raw = "{\n  \"reply\": \"你好。检测到问候信号，正在执行回应程序。\"\n}";
        assertEquals("你好。检测到问候信号，正在执行回应程序。", SiteAiPrompt.plainText(raw, "reply"));
    }

    @Test
    void 拆掉带字面量转义换行与围栏的信封() {
        String raw = "```json\\n{\\n  \"reply\": \"第一行\\\\n第二行\"\\n}```";
        String out = SiteAiPrompt.plainText(raw, "reply");
        assertFalse(out.contains("reply"), out);
        assertFalse(out.contains("\"reply\""), out);
        assertTrue(out.contains("第一行"), out);
        assertTrue(out.contains("第二行"), out);
    }

    @Test
    void 字段名不认识也能取到正文() {
        assertEquals("正文在此", SiteAiPrompt.plainText("{\"title\":\"标题\",\"content\":\"正文在此\"}", "reply"));
        assertEquals("收到", SiteAiPrompt.plainText("{\"data\":{\"reply\":\"收到\"}}", "reply"));
        assertEquals("状态一句", SiteAiPrompt.plainText("{\"status\":\"状态一句\"}", "status"));
    }

    @Test
    void 真的是一段带花括号的正文时不要弄丢内容() {
        String raw = "{\"这不是 JSON";
        assertEquals(raw, SiteAiPrompt.plainText(raw, "reply"));

        String plain = "她想了想，说：“{现在}还不能确认。”";
        assertEquals(plain, SiteAiPrompt.plainText(plain, "reply"));
    }

    @Test
    void 空值处理() {
        assertEquals("", SiteAiPrompt.plainText(null, "reply"));
        assertEquals("", SiteAiPrompt.plainText("   ", "reply"));
    }

    @Test
    void 真的换行不要被吃掉() {
        assertEquals("第一行\n第二行", SiteAiPrompt.plainText("第一行\n第二行", "summary"));
    }

    @Test
    void 三段式回复要取终稿段() {
        String raw = "<think>读者在问候我，回一句就好，别用 JSON 字段名以外的花样。</think>\n"
                + "<draft>{\"reply\": \"草稿里随手写的示例，不该被采用\"}</draft>\n"
                + "<final>{\"reply\": \"你好。检测到问候信号，正在执行回应程序。\"}</final>";
        String json = SandboxReplyParser.extractFinalJson(raw);
        assertTrue(json != null, "应该能取到终稿 JSON");
        assertEquals("你好。检测到问候信号，正在执行回应程序。",
                SiteAiPrompt.jsonField(cn.hutool.json.JSONUtil.parseObj(json), "reply"));
    }

    @Test
    void 忘了写final标记时取最后一个分段之后的内容() {
        String raw = "<think>想一下</think>\n<draft>{\"reply\": \"草稿\"}</draft>\n{\"reply\": \"终稿\"}";
        String json = SandboxReplyParser.extractFinalJson(raw);
        assertTrue(json != null, "应该能取到终稿 JSON");
        assertEquals("终稿", SiteAiPrompt.jsonField(cn.hutool.json.JSONUtil.parseObj(json), "reply"));
    }

    @Test
    void 三段式要求里写清了字段与示例() {
        String s = SiteAiPrompt.stageSection("{\"comment\": \"你的评论\"}", "comment");
        assertTrue(s.contains("<think>") && s.contains("<draft>") && s.contains("<final>"), s);
        assertTrue(s.contains("{\"comment\": \"你的评论\"}"), s);
        assertTrue(s.contains("comment"), s);
    }
}
