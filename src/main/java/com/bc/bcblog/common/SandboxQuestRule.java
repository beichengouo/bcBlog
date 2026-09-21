package com.bc.bcblog.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旅人委托的两条纯规则，抽出来是为了能脱离 Spring 直接写单测
 * （规则错了不会报错，只会让 AI 一步通关、或者把对手强度写到离谱的值）。
 */
public final class SandboxQuestRule {

    private SandboxQuestRule() {
    }

    /**
     * 「完成委托能涨多少战斗力」的默认表：类型 × 难度（1~5）。
     *
     * 为什么要有这张表：以前提示词只说"如果奖励里有更好的装备、或你讨伐了对手，可以给 +1~+3"，
     * 实测 178 条行动里只有 1 条改过战斗力——采集、跑腿、修修补补本来就长不了实力，
     * 但没有尺度时 AI 在每个委托上都填 0。有了这张表，讨伐/探索类会被提醒"这一步该判断一下"，
     * 采集与杂活类则明确告诉它"通常就是 0"，既有成长又不至于每个低难度委托都白涨一点。
     *
     * 格式：`类型:难度1,难度2,难度3,难度4,难度5|类型:...`，每档可以写单值 `2` 或区间 `1-3`。
     */
    public static final String DEFAULT_COMBAT_GAIN_TABLE =
            "hunt:0-1,0-1,0-2,1-3,1-3"
                    + "|explore:0,0-1,0-2,0-2,1-3"
                    + "|escort:0,0,0-1,0-1,0-2"
                    + "|gather:0,0,0,0,0-1"
                    + "|chore:0,0,0,0,0";

    /** 单档战力成长的上限：写歪了也不会让一次委托涨出几十点 */
    private static final int COMBAT_GAIN_MAX = 10;

    /** 委托类型（顺序与后台下拉、前端图标一致） */
    public static final String[] QUEST_TYPES = {"hunt", "gather", "escort", "explore", "chore"};

    /** 解析成长表：内置默认打底，配置里写到的类型按档覆盖 */
    public static Map<String, int[][]> parseCombatGainTable(String config) {
        Map<String, int[][]> table = defaultCombatGainTable();
        if (config == null || config.trim().isEmpty()) {
            return table;
        }
        for (String part : config.split("[|｜;；\\n\\r]+")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            // 类型与档位之间允许用英文或中文冒号
            int split = indexOfSeparator(text);
            if (split <= 0) {
                continue;
            }
            String type = text.substring(0, split).trim().toLowerCase();
            if (type.isEmpty()) {
                continue;
            }
            int[][] slots = table.get(type);
            if (slots == null) {
                slots = new int[QUEST_TYPES.length][2];
            }
            String[] values = text.substring(split + 1).split("[,，\\s]+");
            int[] last = null;
            for (int i = 0; i < values.length && i < slots.length; i++) {
                int[] range = parseRange(values[i]);
                if (range != null) {
                    slots[i] = range;
                    last = range;
                }
            }
            // 只填了前几档时，后面的难度沿用最后一档——和进度分档那条「填几档就按几档算」保持一致
            if (last != null) {
                for (int i = Math.min(values.length, slots.length); i < slots.length; i++) {
                    slots[i] = last;
                }
            }
            table.put(type, slots);
        }
        return table;
    }

    /**
     * 这条委托允许的战力成长区间 [下限, 上限]。
     * 类型没在表里（例如以后新加的类型）按 0 处理：宁可不涨，也不要悄悄通胀。
     */
    public static int[] combatGainRange(String config, String type, Integer difficulty) {
        Map<String, int[][]> table = parseCombatGainTable(config);
        int[][] slots = type == null ? null : table.get(type.trim().toLowerCase());
        if (slots == null) {
            return new int[]{0, 0};
        }
        int level = Math.max(1, Math.min(slots.length, difficulty == null ? 1 : difficulty));
        int[] range = slots[level - 1];
        return range == null ? new int[]{0, 0} : range;
    }

    /**
     * 把 AI 这一给的值夹进区间。
     *
     * 规则刻意不对称：
     *   · AI 给 0 或负数（受伤变弱）**原样保留**——涨不涨由 AI 判断，服务端不硬塞；
     *   · 正数才夹：超过上限就截到上限（采集类上限 0，等于"这类活儿涨不了实力"）。
     */
    public static int clampCombatGain(int change, int[] range) {
        if (range == null || change <= 0) {
            return change;
        }
        int low = Math.max(0, range[0]);
        int high = Math.max(low, range[1]);
        return Math.max(low, Math.min(high, change));
    }

    /** 区间文案：`0~1`（写进提示词给 AI 看） */
    public static String rangeText(int[] range) {
        if (range == null) {
            return "0";
        }
        int low = Math.max(0, range[0]);
        int high = Math.max(low, range[1]);
        return low == high ? String.valueOf(low) : low + "~" + high;
    }

    /** 日常修炼（闲着没事主动变强）单次允许的战力成长上限，默认 2 */
    public static int idleTrainMax(String config) {
        if (config == null || config.trim().isEmpty()) {
            return 2;
        }
        try {
            return Math.max(1, Math.min(5, Integer.parseInt(config.trim())));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static int indexOfSeparator(String text) {
        int half = text.indexOf(':');
        int full = text.indexOf('：');
        if (half < 0) {
            return full;
        }
        if (full < 0) {
            return half;
        }
        return Math.min(half, full);
    }

    /** 解析 `1-3` / `1~3` / `2`；写坏了返回 null（保留原来的档位） */
    private static int[] parseRange(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split("[-~～]");
        try {
            int low = Integer.parseInt(parts[0].trim());
            int high = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : low;
            if (low > high) {
                int tmp = low;
                low = high;
                high = tmp;
            }
            return new int[]{Math.max(0, Math.min(COMBAT_GAIN_MAX, low)),
                    Math.max(0, Math.min(COMBAT_GAIN_MAX, high))};
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, int[][]> defaultCombatGainTable() {
        Map<String, int[][]> table = new LinkedHashMap<>();
        for (String part : DEFAULT_COMBAT_GAIN_TABLE.split("\\|")) {
            int split = indexOfSeparator(part);
            String type = part.substring(0, split).trim();
            String[] values = part.substring(split + 1).split(",");
            int[][] slots = new int[values.length][2];
            for (int i = 0; i < values.length; i++) {
                int[] range = parseRange(values[i]);
                slots[i] = range == null ? new int[]{0, 0} : range;
            }
            table.put(type, slots);
        }
        return table;
    }

    /**
     * 进度推进：**只增不减**，且单步最多 +stepMax。
     *
     * AI 经常"这一步把委托做完了"就直接写 100，所以即使它写了 100，
     * 也只会涨到 old + stepMax；写小了（或写了负数）则视为没推进。
     *
     * @param old         之前的进度（会被夹到 0~100）
     * @param aiProgress  AI 给的进度；null 或负数表示"这一步没推进"
     * @param stepMax     单步上限
     * @return 新的进度（0~100）
     */
    public static int apply(int old, Integer aiProgress, int stepMax) {
        int safeOld = Math.max(0, Math.min(100, old));
        if (aiProgress == null || aiProgress < 0) {
            return safeOld;
        }
        int step = Math.max(1, stepMax);
        int target = Math.max(safeOld, aiProgress);
        return Math.min(100, Math.min(safeOld + step, target));
    }

    /**
     * 讨伐类委托的对手战斗力：夹到目标地点的战力区间里。
     * 地点没设区间（都为 0）或没给战力时就原样返回。
     */
    public static Integer clampPower(Integer power, int min, int max) {
        if (power == null || power <= 0) {
            return power;
        }
        if (min <= 0 && max <= 0) {
            return power;
        }
        int low = min;
        int high = max;
        if (high <= 0) {
            high = low;
        }
        if (low <= 0) {
            low = high;
        }
        return Math.max(low, Math.min(high, power));
    }

    /**
     * 按难度取「单步进度上限」。
     *
     * 背景：以前是一个全局数字（默认 40，实测被管理员调成 55），一视同仁地卡所有委托——
     * 对难度 1 的杂活太苛刻（「清点库房」明明一步能做完，却被卡在 55% 硬拖到第二天，
     * 而且 AI 那一步的叙述里已经写着「完成并领到报酬」，剧情和状态就岔开了）；
     * 对难度 5 的讨伐又太宽松。
     *
     * 现在按难度分档，例如默认 "100,70,50,35,20"：
     *   难度 1 简单 → 100（允许一步做完）、2 一般 → 70、3 棘手 → 50、4 危险 → 35、5 凶险 → 20。
     * 管理员可以在后台自行改这一串数字。
     *
     * @param config     形如 "100,70,50,35,20"，按难度 1~5 依次对应；为空或解析不出时用 fallback
     * @param difficulty 委托难度（越界会被夹到已有档位区间内）
     * @param fallback   兜底值（原来的 sandbox_quest_progress_step_max）
     * @return 单步上限（1~100）
     */
    public static int stepMaxByDifficulty(String config, int difficulty, int fallback) {
        int[] table = parseStepTable(config, Math.max(1, Math.min(100, fallback)));
        if (table.length == 0) {
            return Math.max(1, Math.min(100, fallback));
        }
        int level = Math.max(1, Math.min(table.length, difficulty));
        return Math.max(1, Math.min(100, table[level - 1]));
    }

    /** 解析分档配置；单个数字写坏了就用 fallback 顶替它；整串为空则返回空数组 */
    private static int[] parseStepTable(String config, int fallback) {
        if (config == null || config.trim().isEmpty()) {
            return new int[0];
        }
        String[] parts = config.split("[,，\\s]+");
        java.util.List<Integer> values = new java.util.ArrayList<>();
        for (String part : parts) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                value = fallback;
            }
            values.add(Math.max(1, Math.min(100, value)));
        }
        int[] table = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            table[i] = values.get(i);
        }
        return table;
    }
}
