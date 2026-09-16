package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙盒地图几何算法的单元测试。
 * 重点验证：多边形判定、形心兜底、区域关系（相离/交叉/包含）、自交检测，
 * 以及「矩形转 4 顶点多边形后判定结果与旧矩形逻辑完全一致」这条回归。
 */
class SandboxGeoTest {

    /** 10×10 的正方形 */
    private List<double[]> square() {
        return SandboxGeo.rectPolygon(10, 10, 10, 10);
    }

    /** 一个 C 形（凹多边形）：面积形心会落在缺口里，用来验证兜底逻辑 */
    private List<double[]> cShape() {
        List<double[]> polygon = new ArrayList<>();
        polygon.add(new double[]{0, 0});
        polygon.add(new double[]{30, 0});
        polygon.add(new double[]{30, 10});
        polygon.add(new double[]{10, 10});
        polygon.add(new double[]{10, 30});
        polygon.add(new double[]{0, 30});
        return polygon;
    }

    @Test
    @DisplayName("点在多边形内 / 外的判定（含凹多边形缺口、点在边上）")
    void testContains() {
        List<double[]> square = square();
        assertTrue(SandboxGeo.contains(square, 15, 15), "正方形中心应命中");
        assertFalse(SandboxGeo.contains(square, 25, 15), "正方形右侧外面不应命中");
        assertTrue(SandboxGeo.contains(square, 10, 15), "落在边上应算命中");

        List<double[]> cShape = cShape();
        assertTrue(SandboxGeo.contains(cShape, 5, 5), "C 形的一角应命中");
        assertFalse(SandboxGeo.contains(cShape, 25, 25), "C 形缺口里不应命中");
    }

    @Test
    @DisplayName("面积与形心：正方形形心在中心，C 形的标注点必须落在区域内")
    void testAreaAndLabelPoint() {
        List<double[]> square = square();
        assertEquals(100d, SandboxGeo.area(square), 1e-6);
        double[] center = SandboxGeo.centroid(square);
        assertEquals(15d, center[0], 1e-6);
        assertEquals(15d, center[1], 1e-6);

        List<double[]> cShape = cShape();
        double[] raw = SandboxGeo.centroid(cShape);
        assertFalse(SandboxGeo.contains(cShape, raw[0], raw[1]), "前提：C 形的面积形心确实落在区域外");
        double[] label = SandboxGeo.labelPoint(cShape);
        assertTrue(SandboxGeo.contains(cShape, label[0], label[1]), "标注点必须落在区域内");
    }

    @Test
    @DisplayName("区域外的坐标投影到最近边界上")
    void testProject() {
        List<double[]> square = square();
        double[] projected = SandboxGeo.projectToPolygon(square, 100, 15);
        assertEquals(20d, projected[0], 1e-6);
        assertEquals(15d, projected[1], 1e-6);
        assertTrue(SandboxGeo.contains(square, projected[0], projected[1]), "投影结果应落在区域边界上");
        assertEquals(0d, SandboxGeo.distanceToPolygon(square, 15, 15), 1e-6, "区域内的点距离为 0");
        assertEquals(80d, SandboxGeo.distanceToPolygon(square, 100, 15), 1e-6);
    }

    @Test
    @DisplayName("区域关系：相离 / 交叉重叠 / 完全包含")
    void testRelation() {
        List<double[]> a = square();
        List<double[]> far = SandboxGeo.rectPolygon(50, 50, 10, 10);
        assertEquals(SandboxGeo.REL_DISJOINT, SandboxGeo.relation(a, far));

        List<double[]> cross = SandboxGeo.rectPolygon(15, 15, 10, 10);
        assertEquals(SandboxGeo.REL_CROSS, SandboxGeo.relation(a, cross));

        List<double[]> inner = SandboxGeo.rectPolygon(12, 12, 3, 3);
        assertEquals(SandboxGeo.REL_A_INSIDE_B, SandboxGeo.relation(inner, a));
        assertEquals(SandboxGeo.REL_B_INSIDE_A, SandboxGeo.relation(a, inner));
    }

    @Test
    @DisplayName("重叠面积：按占整张地图的百分比算（完全重合 1%，重叠一半 0.5%，贴边 0）")
    void testOverlapPercent() {
        List<double[]> a = square();
        // 10×10 的正方形 = 100 单位²，整张地图 10000 单位²，所以完全重合是 1%
        assertEquals(1d, SandboxGeo.overlapPercent(a, a), 0.02);

        List<double[]> half = SandboxGeo.rectPolygon(15, 10, 10, 10);
        assertEquals(0.5d, SandboxGeo.overlapPercent(a, half), 0.02);

        // 紧贴在一起（共用一条边）必须算 0 重叠，否则合法的贴边会被拦下来
        List<double[]> touching = SandboxGeo.rectPolygon(20, 10, 10, 10);
        assertEquals(0d, SandboxGeo.overlapPercent(a, touching), 0.02);
    }

    @Test
    @DisplayName("重叠容差：贴边/轻微压边放行，真重叠拦下（容差随区域大小自适应）")
    void testOverlapAllowed() {
        // 100 单位² 的方块：容差取 max(10, 3) = 10 单位²
        List<double[]> a = square();

        // 共边 → 0 重叠，放行
        assertTrue(SandboxGeo.overlapAllowed(a, SandboxGeo.rectPolygon(20, 10, 10, 10)));

        // 压边 1 单位宽 × 10 单位长 = 10 单位² → 正好在容差内，放行
        assertTrue(SandboxGeo.overlapAllowed(a, SandboxGeo.rectPolygon(19, 10, 10, 10)));

        // 压边 4 单位宽 × 10 单位长 = 40 单位² → 超过容差，拦下
        assertFalse(SandboxGeo.overlapAllowed(a, SandboxGeo.rectPolygon(16, 10, 10, 10)));

        // 大区域按 3% 放宽：40×40 = 1600 单位² 的区域，容差 48 单位²
        List<double[]> big = SandboxGeo.rectPolygon(0, 0, 40, 40);
        assertTrue(SandboxGeo.overlapAllowed(big, SandboxGeo.rectPolygon(39, 0, 40, 40)), "长边压边 1 单位应放行");
        assertFalse(SandboxGeo.overlapAllowed(big, SandboxGeo.rectPolygon(20, 0, 40, 40)), "盖住一半必须拦下");

        // 重叠比例的提示值：压掉对方一半 → 50%
        assertEquals(50d, SandboxGeo.overlapRatio(a, SandboxGeo.rectPolygon(15, 10, 10, 10)), 0.5);
    }

    @Test
    @DisplayName("自交检测：数字 8 那种画法要能识别出来")
    void testSelfIntersects() {
        assertFalse(SandboxGeo.selfIntersects(square()));
        List<double[]> figureEight = new ArrayList<>(Arrays.asList(
                new double[]{0, 0},
                new double[]{20, 20},
                new double[]{20, 0},
                new double[]{0, 20}
        ));
        assertTrue(SandboxGeo.selfIntersects(figureEight));
    }

    @Test
    @DisplayName("规范化：去掉重复顶点与共线顶点")
    void testNormalize() {
        List<double[]> messy = new ArrayList<>(Arrays.asList(
                new double[]{0, 0},
                new double[]{0, 0},
                new double[]{10, 0},
                new double[]{20, 0},
                new double[]{20, 20},
                new double[]{0, 20}
        ));
        List<double[]> normalized = SandboxGeo.normalize(messy);
        assertEquals(4, normalized.size(), "重复点与共线点应被去掉");
        assertEquals(400d, SandboxGeo.area(normalized), 1e-6);
    }

    @Test
    @DisplayName("JSON 解析与输出（非法输入返回空、越界坐标夹到 0~100）")
    void testJson() {
        List<double[]> polygon = SandboxGeo.parse("[[10,10],[30,10],[30,20],[10,20]]");
        assertEquals(4, polygon.size());
        assertEquals("[[10,10],[30,10],[30,20],[10,20]]", SandboxGeo.toJson(polygon));
        assertTrue(SandboxGeo.parse("不是JSON").isEmpty());
        assertTrue(SandboxGeo.parse(null).isEmpty());
        List<double[]> clamped = SandboxGeo.parse("[[-5,10],[120,10],[30,20]]");
        assertEquals(0d, clamped.get(0)[0], 1e-6);
        assertEquals(100d, clamped.get(1)[0], 1e-6);
    }

    /**
     * 回归测试：老的矩形地点现在按「4 个顶点」参与判定。
     * 用库里真实的 7 个矩形，把整张地图 101×101 个点逐个对比新旧逻辑，必须完全一致，
     * 否则上线后角色会被判到别的区域去。
     */
    @Test
    @DisplayName("回归：矩形转多边形后判定结果与旧矩形逻辑完全一致")
    void testRectEquivalence() {
        int[][] rects = {
                {61, 54, 12, 10}, {40, 25, 12, 7}, {38, 61, 12, 7}, {87, 73, 12, 7},
                {83, 23, 12, 7}, {18, 16, 12, 7}, {14, 82, 12, 7}
        };
        int checked = 0;
        for (int[] rect : rects) {
            List<double[]> polygon = SandboxGeo.rectPolygon(rect[0], rect[1], rect[2], rect[3]);
            for (int x = 0; x <= 100; x++) {
                for (int y = 0; y <= 100; y++) {
                    boolean oldLogic = x >= rect[0] && x <= rect[0] + rect[2]
                            && y >= rect[1] && y <= rect[1] + rect[3];
                    boolean newLogic = SandboxGeo.contains(polygon, x, y);
                    assertEquals(oldLogic, newLogic,
                            "矩形 " + Arrays.toString(rect) + " 在 (" + x + "," + y + ") 的判定不一致");
                    checked++;
                }
            }
        }
        assertEquals(7 * 101 * 101, checked);
    }
}
