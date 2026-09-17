package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 沙盒「坐标 → 实际距离（km）」的计算测试。
 *
 * 重点锁住两件事：
 *   1. 纵轴必须按地图 16:9 折算（9/16）：否则南北方向的距离会凭空大出 1.78 倍，
 *      AI 会误判"太远不去"，或者把赶路时间算成两倍；
 *   2. 时间换算向上取整：宁可多算一分钟，也别让 1 km 变成 0 分钟。
 */
class SandboxDistanceTest {

    /** 地图宽 200 km：横向 1 单位 = 2 km，纵向 1 单位 = 2 × 9/16 = 1.125 km */
    private static final double MAP_WIDTH = 200d;

    @Test
    @DisplayName("单位长度：横向 2 km / 纵向 1.125 km（按 16:9 折算）")
    void testKmPerUnit() {
        assertEquals(2d, SandboxGeo.kmPerUnit(MAP_WIDTH), 1e-9);
        // 非法配置时兜底 200 km，避免除零或距离全变 0
        assertEquals(2d, SandboxGeo.kmPerUnit(0), 1e-9);
        assertEquals(2d, SandboxGeo.kmPerUnit(-50), 1e-9);
    }

    @Test
    @DisplayName("两点距离：横向 10 单位 = 20 km，纵向 10 单位 = 11.25 km")
    void testKmPointToPoint() {
        assertEquals(0d, SandboxGeo.kmPointToPoint(50, 50, 50, 50, MAP_WIDTH), 1e-9);
        assertEquals(20d, SandboxGeo.kmPointToPoint(10, 50, 20, 50, MAP_WIDTH), 1e-9);
        assertEquals(11.25d, SandboxGeo.kmPointToPoint(50, 10, 50, 20, MAP_WIDTH), 1e-9);
        // 斜向：横向 3 单位(6 km)、纵向 4 单位(4.5 km) → 勾股得 7.5 km
        assertEquals(7.5d, SandboxGeo.kmPointToPoint(0, 0, 3, 4, MAP_WIDTH), 1e-9);
    }

    @Test
    @DisplayName("到地点区域的距离：在区域内为 0，区域外按最近边界算")
    void testKmToPolygon() {
        // 10×10 的方形区域（坐标单位）
        List<double[]> polygon = Arrays.asList(
                new double[]{0, 0}, new double[]{10, 0}, new double[]{10, 10}, new double[]{0, 10});
        assertEquals(0d, SandboxGeo.kmToPolygon(polygon, 5, 5, MAP_WIDTH), 1e-9);
        assertEquals(20d, SandboxGeo.kmToPolygon(polygon, 20, 5, MAP_WIDTH), 1e-9);
        assertEquals(11.25d, SandboxGeo.kmToPolygon(polygon, 5, 20, MAP_WIDTH), 1e-9);
    }

    @Test
    @DisplayName("赶路时间：按速度向上取整，速度非法时按步行兜底")
    void testTravelMinutes() {
        assertEquals(0, SandboxGeo.travelMinutes(0, 4));
        assertEquals(0, SandboxGeo.travelMinutes(-5, 4));
        // 20 km 步行 4 km/h = 5 小时
        assertEquals(300, SandboxGeo.travelMinutes(20, 4));
        // 20 km 骑乘 20 km/h = 1 小时
        assertEquals(60, SandboxGeo.travelMinutes(20, 20));
        // 向上取整：1 km 步行也要算 15 分钟，不能变成 0
        assertEquals(15, SandboxGeo.travelMinutes(1, 4));
        assertEquals(45, SandboxGeo.travelMinutes(3, 0));
        assertEquals(45, SandboxGeo.travelMinutes(3, -1));
    }

    @Test
    @DisplayName("时间文案：分钟 / 小时 / 天")
    void testMinutesText() {
        assertEquals("0 分钟", SandboxGeo.minutesText(0));
        assertEquals("45 分钟", SandboxGeo.minutesText(45));
        assertEquals("1 小时", SandboxGeo.minutesText(60));
        assertEquals("1 小时 30 分钟", SandboxGeo.minutesText(90));
        assertEquals("5 小时", SandboxGeo.minutesText(300));
        assertEquals("1 天 1 小时", SandboxGeo.minutesText(25 * 60));
        assertEquals("2 天", SandboxGeo.minutesText(48 * 60));
    }
}
