package com.bc.bcblog.common;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 「这次输出可疑吗」的规则检查。
 *
 * 用途：AI 输出自查很贵（每次行动多一次调用），所以默认改成**只在这份输出可疑时**才去自查。
 * 这里只做便宜的、能 100% 判定的检查——命中任何一条就返回一句"问题描述"，
 * 那句话会被塞进自查提示词里（"系统检测到以下问题，必须修正：…"），比让模型自己找有效得多。
 *
 * 注意：这里**只判断、不修改**。能直接规则化修掉的（物品名中英混排、花费超上限）由各自的代码处理，
 * 这里负责把"可能需要 AI 复核"的情况挑出来。
 */
public final class SandboxOutputIssues {

    /** 名字里出现 2 个以上连续英文字母（剑与魔法世界观里基本可以判定是混排/错名） */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{2,}");

    private SandboxOutputIssues() {
    }

    /**
     * 检查一次行动输出。
     *
     * @param obj            AI 返回的 JSON
     * @param locations      可用地点名
     * @param companions     可用角色名
     * @param spendLimit     本次允许的单次花费上限（负数超过它就算可疑）
     */
    public static List<String> find(JSONObject obj, List<String> locations, List<String> companions, int spendLimit) {
        List<String> issues = new ArrayList<>();
        if (obj == null) {
            return issues;
        }
        // 1. 物品名（背包变化 / 集市购买）里混了英文
        JSONObject items = obj.getJSONObject("items_change");
        if (items != null) {
            for (String key : items.keySet()) {
                if (key != null && LATIN_WORD.matcher(key).find()) {
                    issues.add("items_change 里的物品名「" + key + "」混了英文，应该改成中文");
                }
            }
        }
        JSONArray shopBuy = obj.getJSONArray("shop_buy");
        if (shopBuy != null) {
            for (Object element : shopBuy) {
                String name = element instanceof JSONObject ? ((JSONObject) element).getStr("name")
                        : Convert.toStr(element);
                if (name != null && LATIN_WORD.matcher(name).find()) {
                    issues.add("shop_buy 里的商品名「" + name + "」混了英文，应该用集市的商品名");
                }
            }
        }
        // 2. 花钱超过上限（赚钱也设一个宽松阈值，避免凭空上万金币）
        int coinsChange = Convert.toInt(obj.get("coins_change"), 0);
        if (coinsChange < -Math.max(1, spendLimit)) {
            issues.add("coins_change=" + coinsChange + " 超过了单次花费上限 " + spendLimit + "，必须与 actions 的实际花销相称");
        }
        if (coinsChange > 200) {
            issues.add("coins_change=" + coinsChange + " 收入过大，日常行动不可能赚这么多");
        }
        // 3. 地点名不在可用列表里（可能是自己编的地点）
        String location = obj.getStr("location");
        if (location != null && !location.trim().isEmpty()
                && locations != null && !locations.contains(location.trim())) {
            issues.add("location=「" + location + "」不在【可用地点】里");
        }
        // 4. 互动对象的名字不在可用角色里
        JSONArray companionsArray = obj.getJSONArray("companions");
        if (companionsArray != null) {
            for (Object element : companionsArray) {
                String name = Convert.toStr(element);
                if (name != null && !name.trim().isEmpty()
                        && (companions == null || !companions.contains(name.trim()))) {
                    issues.add("companions 里的「" + name + "」不是世界里的角色");
                }
            }
        }
        // 5. 状态数值越界
        JSONObject status = obj.getJSONObject("status");
        if (status != null) {
            for (String key : status.keySet()) {
                if ("心情".equals(key)) {
                    continue;
                }
                Object value = status.get(key);
                if (value instanceof Number) {
                    int number = ((Number) value).intValue();
                    if (number < 0 || number > 100) {
                        issues.add("status 里的「" + key + "=" + number + "」超出 0~100");
                    }
                }
            }
        }
        return issues;
    }
}
