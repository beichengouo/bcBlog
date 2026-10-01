package com.bc.bcblog.common;

/**
 * 轻量 Markdown → HTML 转换（够用就好，不引第三方依赖）。
 *
 * 为什么需要：文章正文在前台是用 v-html 渲染的，后台编辑器产出的是 HTML（含 h2/code/pre 等）；
 * 而 AI 更擅长写 Markdown。所以让 AI 写 Markdown，落库前在这里转成 HTML。
 *
 * 支持：## / ### 标题、- 列表、> 引用、**强调**、`行内代码`、``` 代码块、| 表格 |、段落与段内换行。
 *
 * 安全前提：**所有文本先做 HTML 转义**，再拼我们自己的标签，
 * 所以模型输出里即使混进 &lt;script&gt; 也只会被当成纯文字显示。
 */
public final class MarkdownLite {

    private MarkdownLite() {
    }

    /** 一篇文章最多分多少段（防止模型把整篇挤成一段） */
    private static final int MAX_PARAGRAPH_CHARS = 220;

    public static String toHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        String text = markdown.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] blocks = text.split("\n\\s*\n");
        StringBuilder sb = new StringBuilder();
        boolean inList = false;
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        for (String rawBlock : blocks) {
            String block = rawBlock.trim();
            if (block.isEmpty()) {
                continue;
            }
            // 代码块：``` 开头，到下一个 ``` 结束（跨块）
            if (block.startsWith("```")) {
                if (!inCode) {
                    if (inList) {
                        sb.append("</ul>");
                        inList = false;
                    }
                    inCode = true;
                    code.setLength(0);
                    int firstLine = block.indexOf('\n');
                    code.append(firstLine < 0 ? "" : block.substring(firstLine + 1));
                } else {
                    code.append("\n").append(block.replace("```", ""));
                }
                if (block.endsWith("```") && block.length() > 3 && inCode) {
                    int last = code.lastIndexOf("```");
                    if (last >= 0) {
                        code.setLength(last);
                    }
                    sb.append("<pre><code>").append(escape(code.toString().trim())).append("</code></pre>");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                code.append("\n\n").append(block);
                continue;
            }
            // 表格：以 | 开头且第二行是分隔行
            if (block.startsWith("|") && isTable(block)) {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append(tableToHtml(block));
                continue;
            }
            String[] lines = block.split("\n");
            if (block.startsWith("- ") || block.startsWith("* ") || block.startsWith("· ")) {
                if (!inList) {
                    sb.append("<ul>");
                    inList = true;
                }
                for (String line : lines) {
                    String item = line.trim().replaceFirst("^[-*·]\\s*", "");
                    sb.append("<li>").append(inline(item)).append("</li>");
                }
                continue;
            }
            if (inList) {
                sb.append("</ul>");
                inList = false;
            }
            if (block.startsWith("### ")) {
                sb.append("<h3>").append(inline(block.substring(4))).append("</h3>");
                continue;
            }
            if (block.startsWith("## ") || block.startsWith("# ")) {
                sb.append("<h2>").append(inline(block.replaceFirst("^#{1,2}\\s*", ""))).append("</h2>");
                continue;
            }
            if (block.startsWith("> ")) {
                sb.append("<blockquote>").append(inline(block.replaceFirst("^>\\s*", ""))).append("</blockquote>");
                continue;
            }
            sb.append("<p>");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append("<br>");
                }
                sb.append(inline(lines[i].trim()));
            }
            sb.append("</p>");
        }
        if (inList) {
            sb.append("</ul>");
        }
        if (inCode) {
            // 代码块没闭合：按普通代码段收尾，别把内容吞掉
            sb.append("<pre><code>").append(escape(code.toString().trim())).append("</code></pre>");
        }
        return sb.toString();
    }

    /** 兜底：整篇没有分段时按句子切开（模型偶尔会写成一整段） */
    public static String toHtmlWithFallback(String markdown) {
        String text = markdown == null ? "" : markdown.trim();
        if (!text.contains("\n") && text.length() > MAX_PARAGRAPH_CHARS) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                sb.append(c);
                if (c == '。' || c == '！' || c == '？') {
                    count++;
                    if (count % 3 == 0 && i < text.length() - 1) {
                        sb.append("\n\n");
                    }
                }
            }
            text = sb.toString();
        }
        return toHtml(text);
    }

    /** 判断是不是 Markdown 表格（第二行是 |---|---| 这种分隔行） */
    private static boolean isTable(String block) {
        String[] lines = block.split("\n");
        if (lines.length < 2) {
            return false;
        }
        String second = lines[1].trim();
        return second.startsWith("|") && second.replace("|", "").replace("-", "").replace(":", "").trim().isEmpty();
    }

    private static String tableToHtml(String block) {
        String[] lines = block.split("\n");
        StringBuilder sb = new StringBuilder("<table>");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (i == 1) {
                continue; // 分隔行
            }
            if (line.isEmpty() || !line.startsWith("|")) {
                continue;
            }
            String[] cells = line.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
            String tag = i == 0 ? "th" : "td";
            sb.append("<tr>");
            for (String cell : cells) {
                sb.append("<").append(tag).append(">").append(inline(cell.trim())).append("</").append(tag).append(">");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    /** 段内行内标记：先转义，再处理 `代码` 与 **强调** */
    private static String inline(String text) {
        String escaped = escape(text);
        escaped = escaped.replaceAll("`([^`]+?)`", "<code>$1</code>");
        return escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
    }

    /** HTML 转义（模型输出不可信） */
    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
