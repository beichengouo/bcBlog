package com.bc.bcblog.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 纪闻引用的「收紧」规则（与实际写库解耦，便于单测）。
 *
 * 实测问题：两天 44 步里有 27 步挂着同样两条要闻，连标题文字都一样，像在硬蹭。
 * 原因有两层：
 *   1. 提示词里原本写着「只要你还在追查同一条，每一步都要继续填」，等于明着要求重复；
 *   2. 服务端还有个「AI 没填就替它猜」的兜底，会把刚被过滤掉的引用又填回来。
 *
 * 所以现在统一在最后收紧（不管来源是 AI 还是兜底猜测），两道规则：
 *   ① 这一步的行动文本里必须真的出现该要闻标题中的事件名词；
 *   ② 最近几步已经记过的同一条不再重复记录（往前翻上一条就能看到）。
 */
public final class SandboxNewsRef {

    /** 往回看几步：这几步里出现过的引用，本步不再重复记录 */
    public static final int LOOKBACK_STEPS = 3;

    private SandboxNewsRef() {
    }

    /**
     * 收紧一步的纪闻引用。
     *
     * @param ref        这一步的引用，可能是「标题A、标题B」这种多条
     * @param recentRefs 该角色最近几步的引用（从新到旧，可以为 null）
     * @param text       这一步的行动文本（动作 + 概括 + 心声）
     * @return 收紧后的引用；全被丢掉时返回 null
     */
    public static String tighten(String ref, List<String> recentRefs, String text) {
        if (ref == null || ref.trim().isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String title : ref.split("、")) {
            String one = title.trim();
            if (one.isEmpty() || kept.contains(one)) {
                continue;
            }
            // ① 事件名词必须真的出现在行动文本里
            if (SandboxNewsMatcher.match(text, Collections.singletonList(one)).isEmpty()) {
                continue;
            }
            // ② 最近几步记过的，不再重复
            if (recentlyUsed(one, recentRefs)) {
                continue;
            }
            kept.add(one);
        }
        return kept.isEmpty() ? null : String.join("、", kept);
    }

    /** 最近几步的引用里有没有这一条 */
    private static boolean recentlyUsed(String title, List<String> recentRefs) {
        if (recentRefs == null || recentRefs.isEmpty()) {
            return false;
        }
        int checked = 0;
        for (String previous : recentRefs) {
            if (checked++ >= LOOKBACK_STEPS) {
                break;
            }
            if (previous == null) {
                continue;
            }
            if (Arrays.asList(previous.split("、")).contains(title)) {
                return true;
            }
        }
        return false;
    }
}
