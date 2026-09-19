package com.bc.bcblog.common;

/**
 * 旅人委托的两条纯规则，抽出来是为了能脱离 Spring 直接写单测
 * （规则错了不会报错，只会让 AI 一步通关、或者把对手强度写到离谱的值）。
 */
public final class SandboxQuestRule {

    private SandboxQuestRule() {
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
