package com.bc.bcblog.common;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 每日记忆总结的正文清洗。
 *
 * 提示词里已经写明「只输出这段回忆本身，不要标题、日期、分点符号、解释或 JSON」，
 * 但小模型（实测 flash 系列）经常自作主张包一层 JSON：数组里放一个字符串、
 * 或者放 [{"character":"卡恩","date":"…","narrative":"……"}]，有时还套一层代码块。
 *
 * 这层壳会被原样存进 sandbox_memory，第二天再原样拼进提示词的【最近的记忆】——
 * 角色看到的是满屏引号、方括号和转义的换行，既浪费 token，又容易带得模型也开始输出 JSON。
 * 所以入库前统一洗一遍：能当 JSON 解析就把正文抠出来，抠不出来就按原文使用。
 */
public final class SandboxMemoryText {

    /** 对象里最可能装着正文的键，按优先级排 */
    private static final List<String> TEXT_KEYS =
            Arrays.asList("narrative", "summary", "text", "content", "memory", "body", "回忆", "正文", "内容");
    /** 当成「正文」看待的最短长度：短字符串多半是 character/date 这种元信息，不要混进来 */
    private static final int MIN_TEXT_LENGTH = 20;

    private SandboxMemoryText() {
    }

    /** 把 AI 返回的记忆正文洗干净；剥不出东西时原样返回 */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = stripCodeFence(raw.trim());
        if (text.isEmpty()) {
            return text;
        }
        // 必须"整段就是一个 JSON 结构"才尝试剥离：正文里恰好以 [ 开头（例如「[我在集市看到一个摊子]——…」）
        // 时，宽松的 JSON 解析会把方括号里的内容当成数组，把后面的正文整段丢掉
        char first = text.charAt(0);
        char last = text.charAt(text.length() - 1);
        if ((first == '[' && last == ']') || (first == '{' && last == '}')) {
            String extracted = extract(text);
            if (extracted != null && !extracted.trim().isEmpty()) {
                return extracted.trim();
            }
        }
        return text;
    }

    /** 去掉代码块包裹（三个反引号那种） */
    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        String body = firstLineEnd < 0 ? "" : text.substring(firstLineEnd + 1);
        int fence = body.lastIndexOf("```");
        return (fence >= 0 ? body.substring(0, fence) : body).trim();
    }

    private static String extract(String text) {
        try {
            if (text.charAt(0) == '[') {
                JSONArray array = JSONUtil.parseArray(text);
                List<String> parts = new ArrayList<>();
                for (Object element : array) {
                    String part = textOf(element);
                    if (part != null && !part.isEmpty()) {
                        parts.add(part);
                    }
                }
                return parts.isEmpty() ? null : String.join("\n\n", parts);
            }
            return textOf(JSONUtil.parseObj(text));
        } catch (Exception e) {
            // 不是合法 JSON（例如正文里本来就有方括号）→ 交给调用方按原文处理
            return null;
        }
    }

    /** 从一个 JSON 元素里取正文 */
    private static String textOf(Object element) {
        if (element instanceof CharSequence) {
            String text = element.toString().trim();
            return text.isEmpty() ? null : text;
        }
        if (!(element instanceof JSONObject)) {
            return null;
        }
        JSONObject obj = (JSONObject) element;
        for (String key : TEXT_KEYS) {
            String value = obj.getStr(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        // 没有约定键：把够长的字符串值拼起来（忽略 character/date 这类短元信息）
        List<String> parts = new ArrayList<>();
        for (String key : obj.keySet()) {
            String value = obj.getStr(key);
            if (value != null && value.trim().length() >= MIN_TEXT_LENGTH) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }
}
