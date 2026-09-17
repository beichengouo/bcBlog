package com.bc.bcblog.common;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 沙盒地图几何工具：多边形区域（后台手工套索 / 魔法棒描边）的解析、判定与计算。
 *
 * 坐标统一是地图百分比：x 向右、y 向下，取值 0~100。
 *
 * 注意：前台渲染与位置判定也依赖同一套算法（前端对应文件 frontend/src/utils/sandboxGeo.js），
 * 两边的判定结果必须一致，否则会出现「角色名字说在 A、地图上显示在 B」的不一致。
 */
public final class SandboxGeo {

    /** 地图边长（百分比），整张地图面积为 100 × 100 = 10000 单位² */
    public static final double MAP_SIZE = 100d;

    /**
     * 纵向 1 坐标单位的实际长度 = 横向的多少倍。
     *
     * 前台地图容器是 16:9（frontend 的 .map-stage），横轴 100 单位铺满宽度、纵轴 100 单位铺满高度，
     * 所以纵向 1 单位在屏幕上只有横向的 9/16 长。算实际距离（km）时必须按这个比例折算，
     * 否则南北方向的距离会凭空大出 1.78 倍，AI 会误判"太远不去"或者把赶路时间算爆。
     */
    public static final double Y_UNIT_RATIO = 9d / 16d;

    /**
     * 区域重叠容差（绝对量）：手绘压边的误差，10 单位² 以内算「贴边」放行。
     * 相当于一条 10 单位长、1 单位宽的窄缝。
     */
    public static final double OVERLAP_TOLERANCE_AREA = 10d;

    /**
     * 区域重叠容差（相对量）：不超过较小区域面积的 3%。
     * 大区域按比例放宽，否则长边上的正常压边会被误拦。
     */
    public static final double OVERLAP_TOLERANCE_RATIO = 0.03d;

    /** 边界命中容差（百分比）：点正好落在边上时也算命中 */
    public static final double EDGE_TOLERANCE = 0.5d;

    /** 两块区域的关系：相离 */
    public static final int REL_DISJOINT = 0;
    /** 两块区域的关系：交叉重叠（不允许，需拦截） */
    public static final int REL_CROSS = 1;
    /** 两块区域的关系：A 完全包含在 B 内（允许，嵌套，命中时取面积小的） */
    public static final int REL_A_INSIDE_B = 2;
    /** 两块区域的关系：B 完全包含在 A 内（允许） */
    public static final int REL_B_INSIDE_A = 3;

    /** 网格采样精度：把地图切成 N×N 格估算重叠面积，100 格 = 每格 1 单位² */
    private static final int GRID = 100;

    private SandboxGeo() {
    }

    // ============================== 解析与输出 ==============================

    /** 解析多边形 JSON；格式非法或顶点不足 3 个时返回空列表 */
    public static List<double[]> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<double[]> points = new ArrayList<>();
        try {
            JSONArray array = JSONUtil.parseArray(json);
            for (Object item : array) {
                JSONArray pair = item instanceof JSONArray ? (JSONArray) item : JSONUtil.parseArray(String.valueOf(item));
                if (pair == null || pair.size() < 2) {
                    continue;
                }
                double px = Double.parseDouble(String.valueOf(pair.get(0)));
                double py = Double.parseDouble(String.valueOf(pair.get(1)));
                points.add(new double[]{clampCoord(px), clampCoord(py)});
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return points;
    }

    /** 多边形转 JSON（坐标保留一位小数，整数不带小数点） */
    public static String toJson(List<double[]> polygon) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < polygon.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(number(polygon.get(i)[0])).append(',').append(number(polygon.get(i)[1])).append(']');
        }
        return sb.append(']').toString();
    }

    /** 矩形区域转成 4 个顶点的多边形（老数据迁移 / 编辑时初始形状用） */
    public static List<double[]> rectPolygon(double x, double y, double width, double height) {
        List<double[]> polygon = new ArrayList<>();
        polygon.add(new double[]{x, y});
        polygon.add(new double[]{x + width, y});
        polygon.add(new double[]{x + width, y + height});
        polygon.add(new double[]{x, y + height});
        return polygon;
    }

    /**
     * 规范化多边形：去掉重复顶点与多余共线顶点。
     * 套索点常常挤在一起，保留它们只会让判定变慢、后期也没法编辑。
     */
    public static List<double[]> normalize(List<double[]> polygon) {
        List<double[]> result = new ArrayList<>();
        for (double[] point : polygon) {
            double[] last = result.isEmpty() ? null : result.get(result.size() - 1);
            if (last != null && Math.abs(last[0] - point[0]) < 1e-6 && Math.abs(last[1] - point[1]) < 1e-6) {
                continue;
            }
            result.add(new double[]{point[0], point[1]});
        }
        // 首尾重合
        while (result.size() > 1) {
            double[] first = result.get(0);
            double[] last = result.get(result.size() - 1);
            if (Math.abs(first[0] - last[0]) < 1e-6 && Math.abs(first[1] - last[1]) < 1e-6) {
                result.remove(result.size() - 1);
            } else {
                break;
            }
        }
        // 去掉共线顶点（迭代到稳定；至少保留 3 个）
        boolean changed = true;
        while (changed && result.size() > 3) {
            changed = false;
            for (int i = 0; i < result.size(); i++) {
                double[] prev = result.get((i - 1 + result.size()) % result.size());
                double[] cur = result.get(i);
                double[] next = result.get((i + 1) % result.size());
                if (Math.abs(cross(prev, cur, next)) < 0.5d) {
                    result.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    // ============================== 面积与中心 ==============================

    /** 多边形面积（鞋带公式，不分正负） */
    public static double area(List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return 0d;
        }
        double sum = 0d;
        for (int i = 0; i < polygon.size(); i++) {
            double[] cur = polygon.get(i);
            double[] next = polygon.get((i + 1) % polygon.size());
            sum += cur[0] * next[1] - next[0] * cur[1];
        }
        return Math.abs(sum) / 2d;
    }

    /** 面积加权形心；多边形退化（面积为 0）时退回顶点平均 */
    public static double[] centroid(List<double[]> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return new double[]{50, 50};
        }
        if (polygon.size() < 3) {
            return average(polygon);
        }
        double signedArea = 0d;
        double cx = 0d;
        double cy = 0d;
        for (int i = 0; i < polygon.size(); i++) {
            double[] cur = polygon.get(i);
            double[] next = polygon.get((i + 1) % polygon.size());
            double factor = cur[0] * next[1] - next[0] * cur[1];
            signedArea += factor;
            cx += (cur[0] + next[0]) * factor;
            cy += (cur[1] + next[1]) * factor;
        }
        if (Math.abs(signedArea) < 1e-9) {
            return average(polygon);
        }
        return new double[]{cx / (3d * signedArea), cy / (3d * signedArea)};
    }

    /**
     * 取一个「一定在多边形内部」的点：逐行扫描找最长的内部水平线并取中点。
     * 凹多边形（C 形、回旋镖形）的面积形心可能落在区域外面，标签和角色头像行就会飘出去，
     * 所以这里给形心做兜底。
     */
    public static double[] interiorPoint(List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return centroid(polygon);
        }
        double[] box = bbox(polygon);
        double bestWidth = -1d;
        double[] best = null;
        int steps = 60;
        for (int i = 1; i < steps; i++) {
            double y = box[1] + (box[3] - box[1]) * i / steps;
            List<Double> xs = new ArrayList<>();
            for (int k = 0; k < polygon.size(); k++) {
                double[] p1 = polygon.get(k);
                double[] p2 = polygon.get((k + 1) % polygon.size());
                if ((p1[1] > y) != (p2[1] > y)) {
                    xs.add(p1[0] + (y - p1[1]) / (p2[1] - p1[1]) * (p2[0] - p1[0]));
                }
            }
            if (xs.size() < 2) {
                continue;
            }
            Collections.sort(xs);
            // 偶数-奇数配对即为多边形内部区间
            for (int k = 0; k + 1 < xs.size(); k += 2) {
                double width = xs.get(k + 1) - xs.get(k);
                if (width <= bestWidth) {
                    continue;
                }
                double midX = (xs.get(k) + xs.get(k + 1)) / 2d;
                if (contains(polygon, midX, y)) {
                    bestWidth = width;
                    best = new double[]{midX, y};
                }
            }
        }
        return best == null ? centroid(polygon) : best;
    }

    /** 区域标注点：优先形心，形心落在区域外时退回内部点 */
    public static double[] labelPoint(List<double[]> polygon) {
        double[] center = centroid(polygon);
        if (contains(polygon, center[0], center[1])) {
            return center;
        }
        return interiorPoint(polygon);
    }

    // ============================== 命中与距离 ==============================

    /** 点是否在多边形内（含边界容差） */
    public static boolean contains(List<double[]> polygon, double x, double y) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        if (onEdge(polygon, x, y, EDGE_TOLERANCE)) {
            return true;
        }
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i)[0];
            double yi = polygon.get(i)[1];
            double xj = polygon.get(j)[0];
            double yj = polygon.get(j)[1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 点是否落在多边形边界上（容差 tol） */
    public static boolean onEdge(List<double[]> polygon, double x, double y, double tol) {
        if (polygon == null || polygon.size() < 2) {
            return false;
        }
        for (int i = 0; i < polygon.size(); i++) {
            double[] p1 = polygon.get(i);
            double[] p2 = polygon.get((i + 1) % polygon.size());
            if (pointToSegment(x, y, p1[0], p1[1], p2[0], p2[1]) <= tol) {
                return true;
            }
        }
        return false;
    }

    /** 点到多边形的距离：在内部返回 0，否则返回最近边界的距离 */
    public static double distanceToPolygon(List<double[]> polygon, double x, double y) {
        if (polygon == null || polygon.isEmpty()) {
            return Double.MAX_VALUE;
        }
        if (contains(polygon, x, y)) {
            return 0d;
        }
        if (polygon.size() == 1) {
            return Math.hypot(polygon.get(0)[0] - x, polygon.get(0)[1] - y);
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < polygon.size(); i++) {
            double[] p1 = polygon.get(i);
            double[] p2 = polygon.get((i + 1) % polygon.size());
            best = Math.min(best, pointToSegment(x, y, p1[0], p1[1], p2[0], p2[1]));
        }
        return best;
    }

    /** 把区域外的点投影到最近的边界上（返回边上的最近点） */
    public static double[] projectToPolygon(List<double[]> polygon, double x, double y) {
        double[] best = null;
        double bestDistance = Double.MAX_VALUE;
        if (polygon != null && polygon.size() >= 2) {
            for (int i = 0; i < polygon.size(); i++) {
                double[] p1 = polygon.get(i);
                double[] p2 = polygon.get((i + 1) % polygon.size());
                double[] candidate = closestPointOnSegment(x, y, p1[0], p1[1], p2[0], p2[1]);
                double distance = Math.hypot(candidate[0] - x, candidate[1] - y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        if (best == null) {
            best = centroid(polygon);
        }
        return best;
    }

    // ============================== 区域之间 ==============================

    // ============================== 实际距离（km） ==============================

    /**
     * 地图宽度（km）→ 横向 1 坐标单位等于多少 km。
     * 例：地图宽 200 km 时，横向 1 单位 = 2 km，纵向 1 单位 = 2 × 9/16 = 1.125 km。
     */
    public static double kmPerUnit(double mapWidthKm) {
        double width = mapWidthKm <= 0 ? 200d : mapWidthKm;
        return width / MAP_SIZE;
    }

    /** 两点之间的实际距离（km）：先按 16:9 折算纵轴，再乘单位长度 */
    public static double kmPointToPoint(double x1, double y1, double x2, double y2, double mapWidthKm) {
        double perUnit = kmPerUnit(mapWidthKm);
        double dx = (x1 - x2) * perUnit;
        double dy = (y1 - y2) * perUnit * Y_UNIT_RATIO;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 点到地点区域的实际距离（km）：点在区域内返回 0，否则返回最近的边界距离。
     * 做法是把坐标整体缩放进"km 空间"，再复用普通的点-多边形距离算法。
     */
    public static double kmToPolygon(List<double[]> polygon, double x, double y, double mapWidthKm) {
        if (polygon == null || polygon.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double perUnit = kmPerUnit(mapWidthKm);
        List<double[]> scaled = new ArrayList<>(polygon.size());
        for (double[] point : polygon) {
            scaled.add(new double[]{point[0] * perUnit, point[1] * perUnit * Y_UNIT_RATIO});
        }
        return distanceToPolygon(scaled, x * perUnit, y * perUnit * Y_UNIT_RATIO);
    }

    /** 距离文本：10 km 以内保留一位小数，超出取整，避免提示词里出现 12.3456789 这种数字 */
    public static String kmText(double km) {
        if (km < 0) {
            return "0 km";
        }
        if (km < 10) {
            return String.format(java.util.Locale.ROOT, "%.1f km", km);
        }
        return Math.round(km) + " km";
    }

    /** 按 speedKmh 的速度走完 km 需要多少分钟（向上取整；速度非法时按步行 4 km/h 处理） */
    public static int travelMinutes(double km, double speedKmh) {
        if (km <= 0) {
            return 0;
        }
        double speed = speedKmh <= 0 ? 4d : speedKmh;
        return (int) Math.ceil(km / speed * 60d);
    }

    /** 分钟数转成人话：「45 分钟」「5 小时」「1 天 3 小时」 */
    public static String minutesText(int minutes) {
        if (minutes <= 0) {
            return "0 分钟";
        }
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours < 24) {
            return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
        }
        int days = hours / 24;
        int restHours = hours % 24;
        return restHours == 0 ? days + " 天" : days + " 天 " + restHours + " 小时";
    }

    /** 外接矩形 [minX, minY, maxX, maxY] */
    public static double[] bbox(List<double[]> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return new double[]{50, 50, 50, 50};
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] point : polygon) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /** 多边形是否自交（数字 8 那种画法）：自交会导致归属判定出现「洞里也算命中」的怪结果 */
    public static boolean selfIntersects(List<double[]> polygon) {
        if (polygon == null || polygon.size() < 4) {
            return false;
        }
        int size = polygon.size();
        for (int i = 0; i < size; i++) {
            double[] a1 = polygon.get(i);
            double[] a2 = polygon.get((i + 1) % size);
            for (int j = i + 1; j < size; j++) {
                // 相邻边共享顶点，跳过
                if (j == i || (j + 1) % size == i || (i + 1) % size == j) {
                    continue;
                }
                double[] b1 = polygon.get(j);
                double[] b2 = polygon.get((j + 1) % size);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 两块区域的重叠面积（单位²），用网格采样估算 */
    public static double overlapArea(List<double[]> a, List<double[]> b) {
        if (a == null || b == null || a.size() < 3 || b.size() < 3) {
            return 0d;
        }
        // 外接矩形都不相交就不用采样了
        double[] boxA = bbox(a);
        double[] boxB = bbox(b);
        if (boxA[2] < boxB[0] || boxB[2] < boxA[0] || boxA[3] < boxB[1] || boxB[3] < boxA[1]) {
            return 0d;
        }
        double step = MAP_SIZE / GRID;
        int count = 0;
        for (int i = 0; i < GRID; i++) {
            double x = (i + 0.5d) * step;
            for (int j = 0; j < GRID; j++) {
                double y = (j + 0.5d) * step;
                // 这里必须用「不带边界容差」的严格判定：
                // 否则紧贴在一起的两块区域会因为容差各多算一圈格子，被误报成重叠
                if (containsStrict(a, x, y) && containsStrict(b, x, y)) {
                    count++;
                }
            }
        }
        return count * step * step;
    }

    /** 重叠面积占整张地图的百分比 */
    public static double overlapPercent(List<double[]> a, List<double[]> b) {
        return overlapArea(a, b) / (MAP_SIZE * MAP_SIZE) * 100d;
    }

    /** 重叠面积占「较小那块区域」的百分比（提示语里用它更直观） */
    public static double overlapRatio(List<double[]> a, List<double[]> b) {
        double smaller = Math.min(area(a), area(b));
        if (smaller <= 0) {
            return 0d;
        }
        return Math.min(100d, overlapArea(a, b) / smaller * 100d);
    }

    /**
     * 这点重叠算不算「贴边误差」：容差取「10 单位²」和「较小区域面积 3%」里更大的那个。
     * 小区域靠绝对量兜底（手绘精度有限），大区域靠比例放宽（长边压边是正常的）。
     */
    public static boolean overlapAllowed(List<double[]> a, List<double[]> b) {
        double smaller = Math.min(area(a), area(b));
        double limit = Math.max(OVERLAP_TOLERANCE_AREA, smaller * OVERLAP_TOLERANCE_RATIO);
        return overlapArea(a, b) <= limit;
    }

    /**
     * 判断两块区域的关系：
     * 相离 / 交叉重叠（需拦截）/ 一方完全包含另一方（允许嵌套）。
     */
    public static int relation(List<double[]> a, List<double[]> b) {
        if (a == null || b == null || a.size() < 3 || b.size() < 3) {
            return REL_DISJOINT;
        }
        for (int i = 0; i < a.size(); i++) {
            double[] a1 = a.get(i);
            double[] a2 = a.get((i + 1) % a.size());
            for (int j = 0; j < b.size(); j++) {
                double[] b1 = b.get(j);
                double[] b2 = b.get((j + 1) % b.size());
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return REL_CROSS;
                }
            }
        }
        if (allInside(a, b)) {
            return REL_A_INSIDE_B;
        }
        if (allInside(b, a)) {
            return REL_B_INSIDE_A;
        }
        return REL_DISJOINT;
    }

    /** a 的所有顶点是否都在 b 内 */
    private static boolean allInside(List<double[]> a, List<double[]> b) {
        for (double[] point : a) {
            if (!contains(b, point[0], point[1])) {
                return false;
            }
        }
        return true;
    }

    /** 严格判定（不做边界容差），只给网格采样用 */
    private static boolean containsStrict(List<double[]> polygon, double x, double y) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i)[0];
            double yi = polygon.get(i)[1];
            double xj = polygon.get(j)[0];
            double yj = polygon.get(j)[1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    // ============================== 数学小工具 ==============================

    private static double[] average(List<double[]> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return new double[]{50, 50};
        }
        double sx = 0d;
        double sy = 0d;
        for (double[] point : polygon) {
            sx += point[0];
            sy += point[1];
        }
        return new double[]{sx / polygon.size(), sy / polygon.size()};
    }

    /** 三角形有向面积的两倍（判断共线用） */
    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static double pointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double[] closest = closestPointOnSegment(px, py, x1, y1, x2, y2);
        return Math.hypot(closest[0] - px, closest[1] - py);
    }

    /** 点到线段的最近点 */
    private static double[] closestPointOnSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1e-12) {
            return new double[]{x1, y1};
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0d, Math.min(1d, t));
        return new double[]{x1 + t * dx, y1 + t * dy};
    }

    /** 两条线段是否相交（共线重叠也算相交） */
    private static boolean segmentsIntersect(double[] a1, double[] a2, double[] b1, double[] b2) {
        double d1 = cross(b1, b2, a1);
        double d2 = cross(b1, b2, a2);
        double d3 = cross(a1, a2, b1);
        double d4 = cross(a1, a2, b2);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        if (Math.abs(d1) < 1e-9 && onSegment(b1, b2, a1)) {
            return true;
        }
        if (Math.abs(d2) < 1e-9 && onSegment(b1, b2, a2)) {
            return true;
        }
        if (Math.abs(d3) < 1e-9 && onSegment(a1, a2, b1)) {
            return true;
        }
        return Math.abs(d4) < 1e-9 && onSegment(a1, a2, b2);
    }

    /** 已知共线时，点是否落在该线段范围内 */
    private static boolean onSegment(double[] p1, double[] p2, double[] p) {
        return p[0] >= Math.min(p1[0], p2[0]) - 1e-9 && p[0] <= Math.max(p1[0], p2[0]) + 1e-9
                && p[1] >= Math.min(p1[1], p2[1]) - 1e-9 && p[1] <= Math.max(p1[1], p2[1]) + 1e-9;
    }

    private static double clampCoord(double value) {
        return Math.max(0d, Math.min(MAP_SIZE, value));
    }

    /** 输出用：整数不带小数点，其它保留一位小数 */
    private static String number(double value) {
        double rounded = Math.round(value * 10d) / 10d;
        if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }
}
