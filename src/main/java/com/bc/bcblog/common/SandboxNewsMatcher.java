package com.bc.bcblog.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「这一步涉及了哪条旅人纪闻」的服务端兜底判定。
 *
 * 为什么需要兜底：实测 flash 级模型只有在"纪闻就是这一步的核心目标"时才会填 news_refs，
 * 顺带打听、追查线索时几乎从不填（实测某次两天 20 步里 AI 自己填了 0 次）。
 *
 * 判定思路（收紧版，避免误判）：
 *   · 只看**这一步的行动文本**（动作 + 概括 + 心声），**不看**角色恰好站在哪——否则
 *     "在黑潮城码头打零工"会被误判成涉及"黑潮城码头挖出遗迹残片"；
 *   · 要求行动文本里出现**该条纪闻标题中的事件名词**（袭击、护卫、委托、遗迹、残片…），
 *     且这些词必须同时出现在标题里，即"是真的在说同一件事"，而不是泛泛地"打听消息"。
 */
public final class SandboxNewsMatcher {

    /**
     * 事件名词表：必须与纪闻标题取交集才有意义。
     * 刻意**不包含**「打听 / 听说 / 消息 / 传闻 / 调查 / 议论」这类通用行为词——它们会让
     * "在城里随便打听一句"被误判成涉及某条要闻。
     */
    private static final List<String> EVENT_WORDS = Arrays.asList(
            "袭击", "护卫", "委托", "招募", "遗迹", "残片", "庆典", "祭典", "封印", "异动", "泄露",
            "救援", "通缉", "悬赏", "护送", "商队", "魔物", "宝藏", "遗物", "失踪", "暴动", "瘟疫",
            "狩猎", "讨伐", "采集", "护送队", "商路", "港口", "矿脉");

    private SandboxNewsMatcher() {
    }

    /**
     * 从行动文本里挑出这一步涉及的纪闻标题。
     *
     * @param text   这一步的行动文本（动作 + 概括 + 心声）
     * @param titles 今天所有纪闻的标题
     * @return 命中的标题（保持传入顺序，已去重）；没有命中返回空列表
     */
    public static List<String> match(String text, List<String> titles) {
        List<String> hits = new ArrayList<>();
        if (text == null || text.trim().isEmpty() || titles == null || titles.isEmpty()) {
            return hits;
        }
        for (String title : titles) {
            if (title == null || title.trim().isEmpty() || hits.contains(title)) {
                continue;
            }
            for (String word : EVENT_WORDS) {
                if (title.contains(word) && text.contains(word)) {
                    hits.add(title);
                    break;
                }
            }
        }
        return hits;
    }
}
