package com.bc.bcblog.vo;

import lombok.Data;

/** 沙盒运行参数（后台配置）。 */
@Data
public class SandboxSettingVO {
    /** AI 调用总开关：1 开启，0 关闭 */
    private String enabled;
    /** 行动间隔最小值（分钟） */
    private String intervalMin;
    /** 行动间隔最大值（分钟） */
    private String intervalMax;
    /** 夜间静默开始 HH:mm */
    private String nightStart;
    /** 夜间静默结束 HH:mm */
    private String nightEnd;
    /** 每个角色每天最多自动调用次数 */
    private String dailyLimit;
    /** 旅人低语消耗积分 */
    private String whisperPoints;
    /** AI 输出自查开关：1 开启（每次行动额外调用一次 AI 校验） */
    private String verifyEnabled;
    /** 同一轮行动的时间窗（分钟） */
    private String batchWindowMinutes;
    /** 互动回应链最大深度 */
    private String chainMaxDepth;
    /** 每轮最多触发的回应次数 */
    private String chainLimitPerRound;
    /** 刚行动过的角色不做立即回应的冷却时间（分钟） */
    private String reactionCooldownMinutes;
}
