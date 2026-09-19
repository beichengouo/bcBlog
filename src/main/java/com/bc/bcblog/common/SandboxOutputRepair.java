package com.bc.bcblog.common;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/**
 * 沙盒「掉格式补全」的字段级合并工具。
 *
 * 背景：AI 偶尔会漏掉固定字段（例如 next_after_minutes），或者干脆没按 JSON 输出。
 * 与其拿完整提示词重跑一次（temperature 0.9 下会写出另一个行动，时间线容易对不上，还费 token），
 * 不如把它自己的输出回传，让它只把缺的字段补上。
 *
 * 但补全调用有个副作用：模型经常借着"补全"把整段重写一遍。
 * 所以补全结果不能直接采用，必须由这里做**字段级合并**：
 * 以原始输出为基准，只把「缺失或非法」的字段用补全结果填上，已有的内容一个字都不覆盖。
 */
public final class SandboxOutputRepair {

    /** 约定 JSON 里的全部字段（顺序与提示词一致，合并时就按这个清单逐个检查） */
    public static final String[] KEYS = {
            "next_after_minutes", "next_after_reason", "location", "sub_location", "x", "y",
            "actions", "inner_voice", "look", "status", "attitude_change", "goal", "coins_change",
            "combat_change", "companions", "favor_changes", "items_change", "shop_buy",
            "quest_take", "quest_progress", "quest_note", "quest_abandon", "news_refs", "summary"
    };

    /** 主调用至少要返回这么长，才值得拿去做补全；空的或太短的只能重跑 */
    private static final int USABLE_MIN_LENGTH = 12;

    private SandboxOutputRepair() {
    }

    /** 主调用的输出里有没有可以用来补全的原文 */
    public static boolean hasUsableContent(String raw) {
        return raw != null && raw.trim().length() >= USABLE_MIN_LENGTH;
    }

    /**
     * 以 base（原始输出）为基准合并 fix（补全结果）：
     * 只覆盖 base 里「缺失或非法」的字段，base 已有的内容一律保留。
     */
    public static JSONObject merge(JSONObject base, JSONObject fix) {
        if (base == null) {
            return fix;
        }
        if (fix == null) {
            return base;
        }
        for (String key : KEYS) {
            Object fixed = fix.get(key);
            if (fixed == null) {
                continue;
            }
            if (needRepair(key, base.get(key))) {
                base.set(key, fixed);
            }
        }
        return base;
    }

    /** 某个字段是不是「缺失或非法」——非法也当缺失处理，用补全结果替换掉 */
    public static boolean needRepair(String key, Object value) {
        if (value == null) {
            return true;
        }
        switch (key) {
            case "next_after_minutes":
                // 必须大于 0 的整数；AI 常偷懒填 0，也算非法
                return Convert.toInt(value, 0) <= 0;
            case "next_after_reason":
            case "location":
            case "sub_location":
            case "inner_voice":
            case "summary":
                return !notBlank(Convert.toStr(value));
            case "x":
            case "y":
            case "coins_change":
                // 0 是合法值，只在字段整个缺失（或不是数字）时补
                return Convert.toInt(value, null) == null;
            case "actions":
                return !(value instanceof JSONArray) || ((JSONArray) value).isEmpty();
            case "status":
                // 状态必须是带内容的对象；空对象等于没给
                return !(value instanceof JSONObject) || ((JSONObject) value).isEmpty();
            case "companions":
            case "news_refs":
                // 数组为空是有意义的（没有互动 / 没听说纪闻），只在字段缺失时补
                return !(value instanceof JSONArray);
            case "favor_changes":
            case "items_change":
                // 空对象同样有意义（没有好感变化 / 没有物品变化）
                return !(value instanceof JSONObject);
            default:
                return false;
        }
    }

    /**
     * items_change 里某一项的数量变化。
     * 兼容两种写法：旧格式 {"物品名": 2}，新格式 {"物品名": {"delta": 2, "description": "..."}}。
     */
    public static int itemDelta(Object value) {
        if (value instanceof JSONObject) {
            JSONObject item = (JSONObject) value;
            Integer delta = Convert.toInt(item.get("delta"), null);
            if (delta == null) {
                // 兼容写成 count / quantity 的情况
                delta = Convert.toInt(item.get("count"), null);
            }
            if (delta == null) {
                delta = Convert.toInt(item.get("quantity"), null);
            }
            return delta == null ? 0 : delta;
        }
        return Convert.toInt(value, 0);
    }

    /** items_change 里某一项的物品描述；没有则返回 null */
    public static String itemDescription(Object value) {
        if (!(value instanceof JSONObject)) {
            return null;
        }
        JSONObject item = (JSONObject) value;
        String text = Convert.toStr(item.get("description"), null);
        if (text == null) {
            text = Convert.toStr(item.get("desc"), null);
        }
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.trim();
    }

    private static boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
