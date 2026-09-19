package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 纪闻引用收紧的测试。
 *
 * 素材是实测的两条要闻：两天 44 步里有 27 步挂着它们，连标题文字都一模一样。
 */
class SandboxNewsRefTest {

    private static final String CARAVAN = "晨雾森林的商队遭遇魔物袭击，正在招募护卫随行";
    private static final String BEAST = "魔物森林多支商队接连失踪，冒险家协会悬赏调查";
    private static final List<String> TITLES = Arrays.asList(CARAVAN, BEAST);

    @Test
    void 行动里真的提到商队就保留() {
        String text = "艾拉在商队驻地打听消息，顺便看了看招募告示。";
        assertEquals(CARAVAN, SandboxNewsRef.tighten(CARAVAN, Collections.emptyList(), text));
    }

    @Test
    void 行动里完全没沾边的引用要丢掉() {
        String text = "在协会资料室里整理卷宗，把羊皮纸按年份归档。";
        assertNull(SandboxNewsRef.tighten(CARAVAN, Collections.emptyList(), text));
    }

    @Test
    void 最近三步记过的同一条不再重复() {
        String text = "又和商队领队确认了一次路线。";
        assertNull(SandboxNewsRef.tighten(CARAVAN, Collections.singletonList(CARAVAN), text));
        // 三步以前的引用不受影响
        List<String> older = Arrays.asList(null, null, null, CARAVAN);
        assertEquals(CARAVAN, SandboxNewsRef.tighten(CARAVAN, older, text));
    }

    @Test
    void 多条引用里只留新的那条() {
        String text = "商队的人和他说起，森林深处还有别的商队失踪。";
        String ref = CARAVAN + "、" + BEAST;
        String kept = SandboxNewsRef.tighten(ref, Collections.singletonList(CARAVAN), text);
        assertEquals(BEAST, kept);
    }

    @Test
    void 空引用与重复条目都处理干净() {
        assertNull(SandboxNewsRef.tighten(null, Collections.emptyList(), "商队"));
        assertNull(SandboxNewsRef.tighten("   ", Collections.emptyList(), "商队"));
        assertEquals(CARAVAN, SandboxNewsRef.tighten(CARAVAN + "、" + CARAVAN,
                Collections.emptyList(), "商队"));
    }

    @Test
    void 事件名词判定沿用匹配器() {
        // 标题里有「遗迹」这类事件名词，行动里只提到地点不算
        String title = "黑潮城码头挖出古文明遗迹残片";
        assertNull(SandboxNewsRef.tighten(title, Collections.emptyList(), "在黑潮城码头打了一天零工。"));
        assertEquals(title, SandboxNewsRef.tighten(title, Collections.emptyList(), "他在码头挖出了一块遗迹残片。"));
    }
}
