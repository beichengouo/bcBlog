package com.bc.bcblog.common;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * 沙盒回复解析：从「回看 → 思考 → 草稿 → 自审 → 终稿」多段式回复里取出最终 JSON。
 *
 * 背景：开启三段式后，模型会先写 &lt;draft&gt; 草稿、&lt;review&gt; 自审，最后才给 &lt;final&gt; 里的 JSON。
 * 以前的解析要求"整段回复就是一个 JSON"，遇到这种输出会直接判为掉格式。
 * 这里做三件事：
 *   1. extractFinalJson：优先取 &lt;final&gt; 里的 JSON；没有标记时取**最后一段**括号平衡的 JSON；
 *   2. parse：包一层，解析失败返回 null（调用方照旧走"掉格式"的补救流程）；
 *   3. displayText：给"实在解析不出来"时的兜底展示用，**去掉草稿与自审**，
 *      保证 &lt;draft&gt;/&lt;review&gt; 这些标记永远不会出现在前台。
 */
public final class SandboxReplyParser {

    /** <final> 的内容（允许没有闭合标签，那就取到文末） */
    private static final Pattern FINAL_BLOCK = Pattern.compile("(?is)<final>(.*?)(?:</final>|$)");
    // 这几段都允许"没有闭合标签"——实测模型经常直接写 <recap> 内容后接 <think>，
    // 所以用"到下一个段标记为止"的宽松匹配，避免兜底展示时把思考内容漏到前台
    private static final Pattern DRAFT_BLOCK = Pattern.compile("(?is)<draft>.*?(?=</?recap>|</?think>|</?review>|</?final>|$)");
    private static final Pattern REVIEW_BLOCK = Pattern.compile("(?is)<review>.*?(?=</?recap>|</?think>|</?draft>|</?final>|$)");
    private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?(?=</?recap>|</?draft>|</?review>|</?final>|$)");
    private static final Pattern RECAP_BLOCK = Pattern.compile("(?is)<recap>.*?(?=</?think>|</?draft>|</?review>|</?final>|$)");
    /** 代码块围栏（有的模型爱把 JSON 包在 ```json 里） */
    private static final Pattern CODE_FENCE = Pattern.compile("(?is)```[a-zA-Z]*\\s*(.*?)```");
    /** 残留的三段标记 */
    private static final Pattern MARKER_TAG = Pattern.compile("(?i)</?(recap|think|draft|review|final)>");

    private SandboxReplyParser() {
    }

    /** 从回复里取出最终 JSON 文本；取不到返回 null */
    public static String extractFinalJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        // 1) 优先 <final> 段（有多个时取最后一个）
        Matcher matcher = FINAL_BLOCK.matcher(text);
        String block = null;
        while (matcher.find()) {
            block = matcher.group(1);
        }
        if (block != null) {
            String json = lastJsonObject(stripFence(block));
            if (json != null) {
                return json;
            }
        }
        // 2) 退化为"整段文本里最后一段合法 JSON"
        return lastJsonObject(stripFence(text));
    }

    /** 解析回复中的最终 JSON；解析不出来返回 null（调用方继续走补救流程） */
    public static JSONObject parse(String raw) {
        String json = extractFinalJson(raw);
        if (json == null) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 兜底展示文本：解析完全失败时前台会看到这段内容，
     * 所以这里必须把草稿与自审去掉（只保留终稿，或去掉标记后的正文）。
     */
    public static String displayText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw;
        Matcher matcher = FINAL_BLOCK.matcher(text);
        String block = null;
        while (matcher.find()) {
            block = matcher.group(1);
        }
        if (block != null && !block.trim().isEmpty()) {
            text = block;
        }
        text = DRAFT_BLOCK.matcher(text).replaceAll("");
        text = REVIEW_BLOCK.matcher(text).replaceAll("");
        text = THINK_BLOCK.matcher(text).replaceAll("");
        text = RECAP_BLOCK.matcher(text).replaceAll("");
        text = MARKER_TAG.matcher(text).replaceAll("");
        return text.trim();
    }

    /** 去掉 ```json ... ``` 围栏，保留里面的内容 */
    private static String stripFence(String text) {
        Matcher matcher = CODE_FENCE.matcher(text);
        String result = null;
        while (matcher.find()) {
            result = matcher.group(1);
        }
        return result == null ? text : result;
    }

    /**
     * 取文本里**最后一个最外层**、且能被 JSON 解析的 {@code {...}}。
     *
     * 注意这里必须是"最外层"：曾经写成"从后往前找第一个左花括号"，结果遇到嵌套对象时
     * 会取到内层的小对象——例如终稿是
     * {@code {"items_change":{"野浆果":{"delta":-1}},"summary":"…"}}，
     * 最后一个左花括号其实是 {@code {"delta":-1}}，于是解析出一个空对象，
     * 行动记录就变成"字段全空"（前台看起来就是一次空白行动）。
     */
    static String lastJsonObject(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        List<String> candidates = outermostObjects(text);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            String candidate = candidates.get(i);
            try {
                JSONUtil.parseObj(candidate);
                return candidate;
            } catch (Exception ignored) {
                // 不是合法 JSON，继续往前找
            }
        }
        return null;
    }

    /** 按出现顺序收集所有"最外层"花括号片段（草稿里出现的花括号也算，由调用方从后往前挑） */
    private static List<String> outermostObjects(String text) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        result.add(text.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return result;
    }

}
