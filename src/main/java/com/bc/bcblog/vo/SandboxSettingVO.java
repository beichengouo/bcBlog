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
    /** 是否开启每日记忆总结 */
    private String memoryEnabled;
    /** 每天生成记忆的时间 HH:mm */
    private String memoryTime;
    /** 提示词里携带最近几天的记忆 */
    private String memoryPromptDays;
    /** 总结后是否立即删除当天行动日志 */
    private String memoryDeleteActs;
    /** 是否由 AI 决定下次行动间隔 */
    private String aiIntervalEnabled;
    /** AI 间隔下限（分钟） */
    private String aiIntervalMin;
    /** AI 间隔上限（分钟） */
    private String aiIntervalMax;
    /** 旅人纪闻栏目名称 */
    private String newsTitle;
    /** 是否启用旅人纪闻 */
    private String newsEnabled;
    /** 每次生成几条事件 */
    private String newsPerGenerate;
    /** 生成事件使用的 AI 服务商 ID */
    private String newsProviderId;
    /** 生成事件使用的模型 */
    private String newsModel;
    /** 生成事件时的附加要求 */
    private String newsPromptExtra;
    /** 是否每天定时自动生成纪闻 */
    private String newsAutoEnabled;
    /** 自动生成纪闻的时间 HH:mm */
    private String newsAutoTime;
    /** 系统级 AI 调用（定时行动 / 记忆总结）使用的模型，留空则用角色自身模型 */
    private String systemModel;
}
