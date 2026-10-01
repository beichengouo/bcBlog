package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章/资源正文的「降权净化」：给非超级管理员写的内容用。
 *
 * 要同时满足两点：
 *   1. 能执行代码的东西必须摘干净（这是普通管理员→超管的提权通道）；
 *   2. 正常排版必须原样保留（富文本编辑器写出来的 alignment、字号、图片都不能丢）。
 */
class HtmlSanitizerTest {

    @Test
    void 脚本与内嵌页面连内容一起摘掉() {
        String html = "<p>正文</p><script>alert(1)</script><iframe src=\"//evil\"></iframe><p>结尾</p>";
        String out = HtmlSanitizer.stripDangerous(html);
        assertFalse(out.contains("script"), out);
        assertFalse(out.contains("alert"), out);
        assertFalse(out.contains("iframe"), out);
        assertTrue(out.contains("正文") && out.contains("结尾"), out);
    }

    @Test
    void 事件属性与伪协议一起清掉() {
        String html = "<img src=\"http://a/b.png\" onerror=\"alert(1)\">"
                + "<a href=\"javascript:alert(2)\">点我</a>"
                + "<p onclick='evil()'>段落</p>";
        String out = HtmlSanitizer.stripDangerous(html);
        assertFalse(out.toLowerCase().contains("onerror"), out);
        assertFalse(out.toLowerCase().contains("onclick"), out);
        assertFalse(out.toLowerCase().contains("javascript:"), out);
        assertTrue(out.contains("http://a/b.png"), out);
        assertTrue(out.contains("点我"), out);
    }

    @Test
    void 正常排版保持不变() {
        String html = "<h2 style=\"text-align:center\">标题</h2>"
                + "<p style=\"color:#f00\">正文 <strong>加粗</strong></p>"
                + "<img src=\"/uploads/x.png\" width=\"300\">"
                + "<ul><li>一</li></ul>";
        assertEquals(html, HtmlSanitizer.stripDangerous(html));
    }

    @Test
    void 空值原样返回() {
        assertNull(HtmlSanitizer.stripDangerous(null));
        assertEquals("", HtmlSanitizer.stripDangerous(""));
    }

    @Test
    void 单独的未闭合脚本标签也摘掉() {
        String out = HtmlSanitizer.stripDangerous("<p>a</p><style>body{display:none}</style>");
        assertFalse(out.toLowerCase().contains("style"), out);
        assertTrue(out.contains("a"), out);
    }
}
