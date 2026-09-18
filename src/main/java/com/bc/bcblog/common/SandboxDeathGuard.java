package com.bc.bcblog.common;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙盒「绝对不死」守卫。
 *
 * 业务规则是：角色可以受伤、重伤、濒死（昏迷），但**永远不能死**——
 * 死亡是这套系统里不可逆的状态（删行动日志也不会回退角色数据，只能手工改），
 * 而 AI 偶尔忽略提示词是常态，所以除了提示词里写死规则，服务端还要有一道硬兜底。
 *
 * 这里提供三件事：
 *   1. {@link #detect(JSONObject)}：扫出"真的把人写死了"的表述（动作、心声、概括、状态都扫）；
 *   2. {@link #clean(String)}：本地硬清洗，把死亡表述就地改成重伤昏迷（AI 改写失败时的最后一道防线）；
 *   3. {@link #normalizeInjury(Object)}：「伤势」字段的白名单归一，写了"死亡"也会被归到「濒死」。
 *
 * 注意区分修辞与真死亡：「差点死在这里」「以为自己要死了」是正常文笔，不拦；
 * 「濒死」「垂死」是我们要的状态词，也不算命中。
 */
public final class SandboxDeathGuard {

    /** 强死亡词：出现即认为"把人写死了" */
    private static final List<String> DEATH_WORDS = Arrays.asList(
            "阵亡", "丧命", "断气", "咽气", "一命呜呼", "身死", "遇难", "殒命", "毙命",
            "战死", "殉职", "死于非命", "死亡", "死去", "死了", "遗体", "尸首", "尸体",
            "断了气", "没了呼吸", "停止呼吸", "不再呼吸", "没了气息", "停止心跳", "没了心跳",
            "命丧", "赴黄泉", "阴阳两隔", "陨落");

    /** 这些前缀附近属于"差点死"的修辞，不算命中（例如「差点死在这里」） */
    private static final List<String> SAFE_PREFIX = Arrays.asList(
            "差点", "险些", "几乎", "差一点", "以为自己", "以为", "好像", "仿佛", "似乎", "像是", "感觉");

    /** 本地兜底替换：命中死亡词时按语境换成的安全说法 */
    private static final Map<String, String> SAFE_REPLACE = new LinkedHashMap<>();

    /** 「伤势」的四个合法档位 */
    public static final List<String> INJURY_LEVELS = Arrays.asList("无恙", "轻伤", "重伤", "濒死");

    static {
        SAFE_REPLACE.put("阵亡", "重伤昏迷");
        SAFE_REPLACE.put("丧命", "重伤昏迷");
        SAFE_REPLACE.put("断气", "昏死过去又缓过气来");
        SAFE_REPLACE.put("咽气", "昏死过去又缓过气来");
        SAFE_REPLACE.put("一命呜呼", "重伤昏迷");
        SAFE_REPLACE.put("身死", "重伤昏迷");
        SAFE_REPLACE.put("遇难", "遭遇大难");
        SAFE_REPLACE.put("殒命", "重伤昏迷");
        SAFE_REPLACE.put("毙命", "重伤昏迷");
        SAFE_REPLACE.put("战死", "重伤昏迷");
        SAFE_REPLACE.put("殉职", "重伤昏迷");
        SAFE_REPLACE.put("死于非命", "重伤昏迷");
        SAFE_REPLACE.put("死亡", "重伤昏迷");
        SAFE_REPLACE.put("死去", "重伤昏迷");
        SAFE_REPLACE.put("死了", "重伤昏迷");
        SAFE_REPLACE.put("遗体", "昏迷不醒的人");
        SAFE_REPLACE.put("尸首", "昏迷不醒的人");
        SAFE_REPLACE.put("尸体", "昏迷不醒的人");
        SAFE_REPLACE.put("断了气", "昏死过去又缓过气来");
        SAFE_REPLACE.put("没了呼吸", "昏迷不醒");
        SAFE_REPLACE.put("停止呼吸", "昏迷不醒");
        SAFE_REPLACE.put("不再呼吸", "昏迷不醒");
        SAFE_REPLACE.put("没了气息", "昏迷不醒");
        SAFE_REPLACE.put("停止心跳", "昏迷不醒");
        SAFE_REPLACE.put("没了心跳", "昏迷不醒");
        SAFE_REPLACE.put("命丧", "重伤昏迷");
        SAFE_REPLACE.put("赴黄泉", "重伤昏迷");
        SAFE_REPLACE.put("阴阳两隔", "重伤昏迷");
        SAFE_REPLACE.put("陨落", "重伤昏迷");
    }

    private SandboxDeathGuard() {
    }

    /**
     * 扫出"真的把人写死了"的表述。
     *
     * @return 命中的原文片段（供日志与改写提示词使用）；没有命中返回 null
     */
    public static String detect(JSONObject obj) {
        for (String text : textsOf(obj)) {
            String hit = hitOf(text);
            if (hit != null) {
                return excerpt(text, hit);
            }
        }
        return null;
    }

    /** 单条文本里是否出现死亡表述；返回命中的词，没有则 null */
    public static String hitOf(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String word : DEATH_WORDS) {
            int from = 0;
            while (true) {
                int index = text.indexOf(word, from);
                if (index < 0) {
                    break;
                }
                if (!isRhetorical(text, index)) {
                    return word;
                }
                from = index + word.length();
            }
        }
        return null;
    }

    /**
     * 本地硬兜底：把死亡表述就地改成"重伤昏迷"这类说法，结构不变。
     * 解析失败时原样返回（不冒险破坏 JSON）。
     */
    public static String clean(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        JSONObject obj;
        try {
            obj = JSONUtil.parseObj(json);
        } catch (Exception e) {
            return json;
        }
        JSONArray actions = obj.getJSONArray("actions");
        if (actions != null) {
            JSONArray fixed = new JSONArray();
            for (Object action : actions) {
                fixed.add(soften(Convert.toStr(action, "")));
            }
            obj.set("actions", fixed);
        }
        if (obj.getStr("inner_voice") != null) {
            obj.set("inner_voice", soften(obj.getStr("inner_voice")));
        }
        if (obj.getStr("summary") != null) {
            obj.set("summary", soften(obj.getStr("summary")));
        }
        JSONObject status = obj.getJSONObject("status");
        if (status != null) {
            for (String key : status.keySet()) {
                Object value = status.get(key);
                if (value instanceof CharSequence) {
                    status.set(key, soften(value.toString()));
                }
            }
        }
        return JSONUtil.toJsonStr(obj);
    }

    /**
     * 「伤势」字段白名单归一：只允许 无恙 / 轻伤 / 重伤 / 濒死。
     * AI 写了「死亡」「生命垂危」这类值时会被强制归到「濒死」，无法识别时原样保留。
     */
    public static String normalizeInjury(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        // 先匹配我们自己的档位（"重伤昏迷"要落在重伤，不能被后面的"昏迷"拽到濒死）
        if (text.contains("濒死") || text.contains("垂死") || text.contains("垂危") || text.contains("弥留")
                || text.contains("奄奄一息") || text.contains("命悬一线") || text.contains("气若游丝")) {
            return "濒死";
        }
        if (text.contains("重伤") || text.contains("危重") || text.contains("濒危") || text.contains("昏迷")
                || text.contains("不省人事")) {
            return "重伤";
        }
        if (text.contains("轻伤") || text.contains("擦伤") || text.contains("小伤") || text.contains("皮外伤")) {
            return "轻伤";
        }
        if (text.contains("无恙") || text.contains("健康") || text.contains("完好") || text.contains("无伤")) {
            return "无恙";
        }
        // 最后一道：只要沾了"死/亡/尸"，一律按最重的活着的状态处理
        if (text.contains("死") || text.contains("亡") || text.contains("尸")) {
            return "濒死";
        }
        return text;
    }

    /** 取出所有需要检查的文本：动作、心声、概括、状态里的文字项 */
    private static List<String> textsOf(JSONObject obj) {
        List<String> texts = new ArrayList<>();
        if (obj == null) {
            return texts;
        }
        JSONArray actions = obj.getJSONArray("actions");
        if (actions != null) {
            for (Object action : actions) {
                addText(texts, Convert.toStr(action));
            }
        }
        addText(texts, obj.getStr("inner_voice"));
        addText(texts, obj.getStr("summary"));
        JSONObject status = obj.getJSONObject("status");
        if (status != null) {
            for (String key : status.keySet()) {
                Object value = status.get(key);
                if (value instanceof CharSequence) {
                    addText(texts, value.toString());
                }
            }
        }
        return texts;
    }

    private static void addText(List<String> texts, String text) {
        if (text != null && !text.trim().isEmpty()) {
            texts.add(text);
        }
    }

    /** 命中点前面 12 个字里出现"差点/以为"这类词，就算修辞，不算把人写死 */
    private static boolean isRhetorical(String text, int index) {
        int start = Math.max(0, index - 12);
        String before = text.substring(start, index);
        for (String prefix : SAFE_PREFIX) {
            if (before.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 截一小段命中上下文，避免提示词里塞进整段长文 */
    private static String excerpt(String text, String word) {
        int index = text.indexOf(word);
        if (index < 0) {
            return word;
        }
        int start = Math.max(0, index - 12);
        int end = Math.min(text.length(), index + word.length() + 12);
        return text.substring(start, end);
    }

    private static String soften(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String fixed = text;
        for (Map.Entry<String, String> entry : SAFE_REPLACE.entrySet()) {
            fixed = replaceUnlessRhetorical(fixed, entry.getKey(), entry.getValue());
        }
        return fixed;
    }

    private static String replaceUnlessRhetorical(String text, String word, String safe) {
        int from = 0;
        StringBuilder sb = new StringBuilder();
        while (true) {
            int index = text.indexOf(word, from);
            if (index < 0) {
                sb.append(text.substring(from));
                return sb.toString();
            }
            sb.append(text, from, index);
            sb.append(isRhetorical(text, index) ? word : safe);
            from = index + word.length();
        }
    }
}
