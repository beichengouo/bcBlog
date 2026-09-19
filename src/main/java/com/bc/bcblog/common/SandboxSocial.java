package com.bc.bcblog.common;

/**
 * 「两个人此刻能不能互动」的判定规则（抽出来便于单测）。
 *
 * 原规则只看距离：同一片区域，或者相距不超过 sandbox_social_max_km（默认 30km）就算"附近的人"，
 * 于是可能出现「明明在两个不同的一级地区，却因为地图上这两片区挨得近（不到 30km）而互动起来」，
 * 甚至互相触发回应行动。用户实测提出要加一道保险。
 *
 * 新规则：
 *   ① 必须在**同一个一级地点**（默认强制，可用 sandbox_social_same_area_only 关掉）；
 *   ② 同一级地点内：在同一个二级地点（AI 当场自创的小地方）就算"就在一起"；
 *      否则再看距离，不超过上限也算；
 *   ③ maxKm ≤ 0 表示不限距离，但 ① 仍然生效。
 */
public final class SandboxSocial {

    private SandboxSocial() {
    }

    /**
     * @param sameArea     是否在同一个一级地点
     * @param sameSpot     是否连二级地点都相同（意味着"就在同一处"）
     * @param km           两人的实际距离（km）
     * @param maxKm        互动距离上限（&lt;= 0 表示不限距离）
     * @param sameAreaOnly 是否强制"必须同一级地点"
     */
    public static boolean canInteract(boolean sameArea, boolean sameSpot, double km, int maxKm,
                                      boolean sameAreaOnly) {
        if (sameSpot) {
            return true;
        }
        if (sameAreaOnly && !sameArea) {
            return false;
        }
        return maxKm <= 0 || km <= maxKm;
    }
}
