package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 记忆正文清洗的测试。
 * 样例都是真实跑出来的：flash 模型会把整段回忆包成 JSON 数组，甚至带 character/date 字段。
 */
class SandboxMemoryTextTest {

    private static final String PROSE =
            "此刻坐在古树根部的空隙里，听着林间未歇的雨声，我才觉得今天真是倒霉透顶。";

    @Test
    void 数组里放一个字符串要能剥出来() {
        assertEquals(PROSE, SandboxMemoryText.clean("[\n  \"" + PROSE + "\"\n]"));
    }

    @Test
    void 对象数组要取narrative字段() {
        String raw = "[{\"character\":\"卡恩\",\"date\":\"2024-09-19\",\"narrative\":\"" + PROSE + "\"}]";
        assertEquals(PROSE, SandboxMemoryText.clean(raw));
    }

    @Test
    void 单个对象要取summary字段() {
        assertEquals(PROSE, SandboxMemoryText.clean("{\"summary\":\"" + PROSE + "\"}"));
    }

    @Test
    void 转义换行要还原成真换行() {
        String cleaned = SandboxMemoryText.clean("[\"第一段。\\n\\n第二段。\"]");
        assertTrue(cleaned.contains("\n\n"), "应当还原成真正的换行，而不是留着 \\n 字面量");
    }

    @Test
    void 带代码块包裹也要剥掉() {
        assertEquals(PROSE, SandboxMemoryText.clean("```json\n[\"" + PROSE + "\"]\n```"));
    }

    @Test
    void 没有约定键时把长字符串拼起来且忽略元信息() {
        String cleaned = SandboxMemoryText.clean(
                "[{\"character\":\"卡恩\",\"date\":\"2024-09-19\",\"body\":\"" + PROSE + "\"}]");
        assertTrue(cleaned.contains("古树根部"));
        assertTrue(!cleaned.contains("卡恩"), "character 这种元信息不该混进记忆正文");
    }

    @Test
    void 本来就是纯文本的不要动它() {
        assertEquals(PROSE, SandboxMemoryText.clean(PROSE));
        assertEquals(PROSE, SandboxMemoryText.clean("  " + PROSE + "  "));
    }

    @Test
    void 正文里本来就有方括号时按原文保留() {
        String raw = "[我在集市看到一个摊子]——那是今天的起点。";
        assertEquals(raw, SandboxMemoryText.clean(raw));
    }

    @Test
    void 空值处理() {
        assertNull(SandboxMemoryText.clean(null));
        assertEquals("", SandboxMemoryText.clean("   "));
    }
}
