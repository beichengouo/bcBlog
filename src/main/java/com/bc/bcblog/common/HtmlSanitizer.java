package com.bc.bcblog.common;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文章 HTML 白名单净化。
 *
 * 背景：网站 AI「IRIS」现在直接输出 HTML（和后台的 AI 一键写文章一致），
 * 但她是**无人审核直接发布**的，所以模型输出不能原样入库：
 *   · 允许的标签：标题 / 段落 / 列表 / 引用 / 代码 / 表格 / 强调 / 图片 / 链接等排版用标签；
 *   · 属性只留白名单里的：图片的 src+alt、链接的 href+title、表格单元格的跨行跨列；
 *   · 脚本、样式、iframe、svg 这类直接连同内容一起丢弃；事件属性（onclick…）与 style 一律去掉；
 *   · 链接只允许 http(s)、站内相对路径与锚点，杜绝 javascript: 这类伪协议。
 *
 * 不引第三方依赖：自己扫一遍标签即可，逻辑简单、可控、可单测。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "h2", "h3", "h4", "p", "br", "hr", "strong", "em", "b", "i", "u", "s",
            "blockquote", "code", "pre", "ul", "ol", "li",
            "table", "thead", "tbody", "tr", "th", "td",
            "figure", "figcaption", "sub", "sup", "kbd", "div", "span", "img", "a"));

    /** 自闭合标签（没有 </x>） */
    private static final Set<String> VOID_TAGS = new HashSet<>(Arrays.asList("br", "hr", "img"));

    /** 连内容一起丢掉的标签 */
    private static final Set<String> DROP_WITH_CONTENT =
            new HashSet<>(Arrays.asList("script", "style", "iframe", "svg", "object", "embed", "noscript", "form", "input"));

    /** 各标签允许保留的属性 */
    private static final Map<String, Set<String>> ALLOWED_ATTRS = new HashMap<>();

    static {
        ALLOWED_ATTRS.put("img", new HashSet<>(Arrays.asList("src", "alt", "width", "height")));
        ALLOWED_ATTRS.put("a", new HashSet<>(Arrays.asList("href", "title")));
        ALLOWED_ATTRS.put("td", new HashSet<>(Arrays.asList("colspan", "rowspan")));
        ALLOWED_ATTRS.put("th", new HashSet<>(Arrays.asList("colspan", "rowspan")));
        ALLOWED_ATTRS.put("code", new HashSet<>(Arrays.asList("class")));
    }

    public static String sanitize(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        int len = html.length();
        while (i < len) {
            char c = html.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            int close = html.indexOf('>', i);
            if (close < 0) {
                // 落单的 '<'：转义掉，别让浏览器当标签解析
                out.append("&lt;");
                i++;
                continue;
            }
            String tag = html.substring(i + 1, close).trim();
            i = close + 1;
            if (tag.isEmpty() || tag.startsWith("!") || tag.startsWith("?")) {
                continue; // 注释、DOCTYPE 一律丢弃
            }
            boolean closing = tag.startsWith("/");
            String body = closing ? tag.substring(1).trim() : tag;
            String name = body.split("[\\s/]+")[0].toLowerCase();
            if (name.isEmpty()) {
                continue;
            }
            if (DROP_WITH_CONTENT.contains(name)) {
                if (!closing) {
                    int end = indexOfIgnoreCase(html, "</" + name, i);
                    i = end < 0 ? len : html.indexOf('>', end) + 1;
                }
                continue;
            }
            if (!ALLOWED_TAGS.contains(name)) {
                continue; // 未知标签只去标签、保留文字
            }
            if (closing) {
                if (!VOID_TAGS.contains(name)) {
                    out.append("</").append(name).append(">");
                }
                continue;
            }
            StringBuilder attrs = new StringBuilder();
            Set<String> allowed = ALLOWED_ATTRS.get(name);
            if (allowed != null) {
                for (String[] kv : parseAttrs(body)) {
                    String key = kv[0];
                    String value = kv[1];
                    if (!allowed.contains(key) || value == null) {
                        continue;
                    }
                    if ("src".equals(key) || "href".equals(key)) {
                        if (!safeUrl(value)) {
                            continue;
                        }
                    }
                    if (("width".equals(key) || "height".equals(key)
                            || "colspan".equals(key) || "rowspan".equals(key))
                            && !value.matches("\\d{1,4}")) {
                        continue;
                    }
                    if ("class".equals(key) && !value.matches("[a-zA-Z0-9_\\- ]{1,40}")) {
                        continue;
                    }
                    attrs.append(" ").append(key).append("=\"").append(value.replace("\"", "")).append("\"");
                }
            }
            if ("a".equals(name)) {
                String href = attrValue(body, "href");
                if (href != null && (href.startsWith("http://") || href.startsWith("https://"))) {
                    attrs.append(" target=\"_blank\" rel=\"noopener noreferrer\"");
                }
            }
            out.append("<").append(name).append(attrs).append(">");
            if ("pre".equals(name)) {
                // pre 内部保持原样（通常含 <code>），到 </pre> 之间直接搬运
                int end = indexOfIgnoreCase(html, "</pre", i);
                if (end > 0) {
                    out.append(sanitizePre(html.substring(i, end)));
                    i = html.indexOf('>', end) + 1;
                    out.append("</pre>");
                }
            }
        }
        return out.toString().trim();
    }

    /** pre 里只保留 code 标签，其余内容原样（含换行与缩进） */
    private static String sanitizePre(String inner) {
        String text = inner.replaceAll("(?i)</?code[^>]*>", "");
        return "<code>" + text.replace("<", "&lt;").replace(">", "&gt;") + "</code>";
    }

    /** 纯文本用途（评论 / 回复 / 状态）：去掉所有标签并转义尖括号 */
    public static String stripTags(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("<[^>]*>", "").replace("<", "&lt;").replace(">", "&gt;").trim();
    }

    /**
     * 「降权净化」：给**非超级管理员**写的内容（文章、智库资源）用。
     *
     * 和上面的 sanitize() 不一样：那里是白名单，会把不在名单里的标签与所有 style 一起去掉——
     * 文章是富文本编辑器写出来的，用白名单会把对齐、字号这些内联样式全删光，
     * 普通管理员辛辛苦苦排的版会瞬间变纯文本。
     *
     * 所以这里走"黑名单摘除"：只摘掉真正能执行代码的东西，排版原样保留。
     * 为什么需要它：文章正文在前台是 v-html 直接渲染的，而被授权「文章管理」的普通管理员
     * 能把任意 HTML 存进正文——不处理的话，普通管理员（或账号被盗）就能在超管浏览器里执行脚本，
     * 而超管的登录 token 就在 localStorage 里，等于提权到超管。
     */
    public static String stripDangerous(String html) {
        if (html == null || html.trim().isEmpty()) {
            return html;
        }
        String out = html;
        // 1. 危险标签连同内容一起丢掉（成对出现时）
        out = out.replaceAll("(?is)<\\s*(" + DANGEROUS_TAGS + ")\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>", "");
        // 2. 剩下的单标签 / 未闭合标签：只去标签
        out = out.replaceAll("(?is)<\\s*/?\\s*(" + DANGEROUS_TAGS + ")\\b[^>]*>", "");
        // 3. on* 事件属性：onclick="..." / onerror='...' / onload=alert(1)
        out = out.replaceAll("(?i)\\son[a-z]{3,}\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        // 4. 伪协议：javascript: / vbscript: / data:text/html
        out = out.replaceAll("(?i)(href|src|xlink:href|action|formaction|background)\\s*=\\s*(\"|')?\\s*"
                + "(javascript|vbscript|data\\s*:\\s*text/html)[^\"'>\\s]*", "");
        // 5. CSS 里的老式表达式、以及 url(javascript:...)
        out = out.replaceAll("(?i)expression\\s*\\(", "blocked-css(");
        out = out.replaceAll("(?i)url\\s*\\(\\s*(['\"]?)\\s*javascript:", "url(blocked:");
        return out;
    }

    /** 能执行代码 / 能伪装界面的标签：整块摘掉 */
    private static final String DANGEROUS_TAGS =
            "script|iframe|frame|frameset|object|embed|applet|form|input|button|textarea|select|option|"
                    + "base|link|meta|style|svg|math";

    /** 看起来是 HTML（含块级标签）就用它，否则交给 Markdown 转换器 */
    public static boolean looksLikeHtml(String text) {
        if (text == null) {
            return false;
        }
        return text.matches("(?is).*<\\s*(p|h2|h3|h4|ul|ol|table|blockquote|pre|div)\\b.*");
    }

    private static boolean safeUrl(String url) {
        String v = url.trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://")
                || v.startsWith("/") || v.startsWith("#") || v.startsWith("data:image/");
    }

    private static String attrValue(String body, String key) {
        for (String[] kv : parseAttrs(body)) {
            if (key.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    /** 极简属性解析：支持 key="v" / key='v' / key=v / 裸 key（值为 ""） */
    private static java.util.List<String[]> parseAttrs(String body) {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        String rest = body.replaceFirst("^[^\\s]+", "").trim();
        int i = 0;
        while (i < rest.length()) {
            while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < rest.length() && rest.charAt(i) != '=' && !Character.isWhitespace(rest.charAt(i))) {
                i++;
            }
            if (start == i) {
                break;
            }
            String key = rest.substring(start, i).toLowerCase();
            String value = "";
            while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) {
                i++;
            }
            if (i < rest.length() && rest.charAt(i) == '=') {
                i++;
                while (i < rest.length() && Character.isWhitespace(rest.charAt(i))) {
                    i++;
                }
                if (i < rest.length() && (rest.charAt(i) == '"' || rest.charAt(i) == '\'')) {
                    char quote = rest.charAt(i);
                    int end = rest.indexOf(quote, i + 1);
                    if (end < 0) {
                        end = rest.length();
                    }
                    value = rest.substring(i + 1, end);
                    i = end + 1;
                } else {
                    int end = i;
                    while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) {
                        end++;
                    }
                    value = rest.substring(i, end);
                    i = end;
                }
            }
            list.add(new String[]{key, value});
        }
        return list;
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        return text.toLowerCase().indexOf(needle.toLowerCase(), from);
    }
}
