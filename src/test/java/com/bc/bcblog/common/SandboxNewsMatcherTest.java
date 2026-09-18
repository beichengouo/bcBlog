package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纪闻兜底判定的规则测试。
 * 重点是"收紧"之后的两个方向：该补的要补上，不该补的（只是在同一地点活动）不能补。
 */
class SandboxNewsMatcherTest {

    private static final String NEWS_1 = "晨雾森林的商队遭遇魔物袭击，正在招募护卫随行";
    private static final String NEWS_2 = "冒险家协会发放新人帮扶委托";
    private static final String NEWS_3 = "黑潮城码头挖出古文明遗迹残片";
    private static final List<String> TITLES = Arrays.asList(NEWS_1, NEWS_2, NEWS_3);

    @Test
    void 只是同地点打零工不应算涉及要闻() {
        // 实测的误判场景：在黑潮城码头打工买药，被旧规则误判成"涉及遗迹残片"
        String text = "在码头打零工分拣药草，赚到了来到黑潮城的第一笔钱。在集市买药治愈了轻伤。";
        assertTrue(SandboxNewsMatcher.match(text, TITLES).isEmpty());
    }

    @Test
    void 追查商队袭击要能命中() {
        String text = "向商队护卫递上两枚金币，低声询问袭击发生的具体方位，检查货车上的爪痕。";
        assertEquals(Collections.singletonList(NEWS_1), SandboxNewsMatcher.match(text, TITLES));
    }

    @Test
    void 接取与交付委托要能命中() {
        assertEquals(Collections.singletonList(NEWS_2),
                SandboxNewsMatcher.match("在协会大厅选定了一项清理委托并办理登记。", TITLES));
        assertEquals(Collections.singletonList(NEWS_2),
                SandboxNewsMatcher.match("交付了野猪皮委托，领到了 12 枚金币的报酬。", TITLES));
    }

    @Test
    void 泛泛地打听消息不算() {
        assertTrue(SandboxNewsMatcher.match("在城里随便打听了几句消息，什么也没问到。", TITLES).isEmpty());
    }

    @Test
    void 真的挖到遗迹残片要能命中() {
        assertEquals(Collections.singletonList(NEWS_3),
                SandboxNewsMatcher.match("在码头废墟里翻找，挖出了一块刻着云纹的遗迹残片。", TITLES));
    }

    @Test
    void 空输入不应报错() {
        assertTrue(SandboxNewsMatcher.match(null, TITLES).isEmpty());
        assertTrue(SandboxNewsMatcher.match("随便走走", null).isEmpty());
        assertTrue(SandboxNewsMatcher.match("随便走走", Collections.emptyList()).isEmpty());
    }
}
