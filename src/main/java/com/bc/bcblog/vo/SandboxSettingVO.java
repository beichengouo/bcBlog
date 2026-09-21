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
    /** AI 输出自查开关（旧字段，保留兼容）：1 开启，0 关闭 */
    private String verifyEnabled;
    /** AI 输出自查模式：off 关闭 / suspicious 仅可疑时查（默认） / always 每次都查 */
    private String verifyMode;
    /** 三段式输出（草稿→自审→终稿）：on 开启（默认）/ off 关闭 */
    private String draftMode;
    /** 思考阶段（四段式最前面那段 <think>）：on 开启（默认）/ off 关闭；仅在三段式开启时生效 */
    private String thinkStage;
    /** 四个生成器（角色/纪闻/集市/委托）是否走三段式：three 开启（默认）/ off 关闭；角色行动不适用 */
    private String generatorFlow;
    /** 文风补充：管理员可粘贴酒馆预设里的写作基准段落，会拼进行动提示词 */
    private String styleExtra;
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

    /** 自动生成纪闻的刷新间隔（小时）：24 = 每天一次；填 6 就是一天四次 */
    private String newsIntervalHours;
    /** 系统级 AI 调用（定时行动 / 记忆总结）使用的模型，留空则用角色自身模型 */
    private String systemModel;
    /** 系统级 AI 调用统一使用的服务商 id（留空 = 沿用原来的自动规则：角色绑定的系统服务商，否则默认服务商） */
    private String systemProviderId;
    /** AI 调用失败后的退避起步分钟数（连续失败按 2 倍递增） */
    private String failBackoffBaseMinutes;
    /** AI 调用失败退避的上限分钟数 */
    private String failBackoffMaxMinutes;
    /** 旅人低语总开关：1 开启，0 关闭（前台隐藏入口，接口同时拦截） */
    private String whisperEnabled;
    // ---------------- 旅人集市 ----------------
    /** 前台集市栏目名 */
    private String shopTitle;
    /** 集市总开关 */
    private String shopEnabled;
    /** 是否按间隔自动刷新 */
    private String shopAutoEnabled;
    /** 刷新间隔（小时）：24 = 每天一次，6 = 一天四次 */
    private String shopIntervalHours;
    /** 当天第一次刷新的时间 HH:mm */
    private String shopAutoTime;
    /** 每次刷新生成几件商品 */
    private String shopPerGenerate;
    /** 生成商品用的服务商 id */
    private String shopProviderId;
    /** 生成商品用的模型 */
    private String shopModel;
    /** 生成商品的附加要求 */
    private String shopPromptExtra;
    /** 同一用户对同一商品、每个角色的限购数量 */
    private String shopLimitPerCharacter;
    /** 角色每天最多在集市自购几件（0 = 不限制） */
    private String shopBuyPerDay;
    /** 积分 → 金币 汇率（1 积分换多少金币），前台购买折算与"贡献金币"共用 */
    private String coinRate;
    // ---------------- 旅人委托板 ----------------
    /** 前台委托板栏目名 */
    private String questTitle;
    /** 委托板总开关 */
    private String questEnabled;
    /** 提示词里最多列几条可接委托 */
    private String questVisibleCount;
    /** 每次刷新生成几条委托 */
    private String questPerGenerate;
    /** 生成委托用的服务商 id */
    private String questProviderId;
    /** 生成委托用的模型 */
    private String questModel;
    /** 生成委托的附加要求 */
    private String questPromptExtra;
    /** 单步进度上限（%）：防止 AI 一步把进度从 0 写到 100 */
    private String questProgressStepMax;
    /** 单步进度上限的按难度分档："100,70,50,35,20"（难度 1~5）；留空则用上面的单值 */
    private String questStepMaxByDifficulty;
    /** 完成委托允许的战力成长表："hunt:0-1,0-1,0-2,1-3,1-3|gather:0,0,0,0,0-1"（类型 × 难度 1~5） */
    private String questCombatGain;
    /** 闲着主动修炼时单次允许的战力成长上限（默认 2） */
    private String idleTrainGainMax;
    /** 是否按间隔自动刷新委托 */
    private String questAutoEnabled;
    /** 自动刷新间隔（小时） */
    private String questIntervalHours;
    /** 当天第一次自动刷新的时间 HH:mm */
    private String questAutoTime;
    /** 完成校验没过时，是否再调一次 AI 让它自检修正（1 开 / 0 关） */
    private String questSelfcheck;
    /** 单次行动的**非委托**收入上限（金币）：防止 AI 一句话让角色进账一大笔 */
    private String maxEarnPerAct;
    // ---------------- 距离与交通 ----------------
    /** 地图宽度（km）：横向 100 个坐标单位对应多少公里（默认 200） */
    private String kmMapWidth;
    /** 交通方式与速度：形如「步行:4,骑乘:20,车船:12,飞行:60」 */
    private String travelSpeeds;
    /** 允许互相互动（同行 / 好感度）的最大距离 km，0 = 不限制 */
    private String socialMaxKm;
    /** 是否强制「必须同一个一级地点才能互动」（1 开 / 0 关）：地图上有的一级地点彼此不到 30km */
    private String socialSameAreaOnly;

    /** 装备栏开关：1 开启（默认）/ 0 关闭（关掉后提示词不给装备规则、AI 的装备动作一律忽略） */
    private String equipEnabled;
    /** 装备加成按品质夹取的区间："1-10,10-20,20-30,30-60,60-120"（品质 1~5），管理员可改 */
    private String equipBonusByRarity;

    /** 【最近行动】里最近几步给"完整动作 + 心声"（默认 1，0 = 全部只给摘要） */
    private String fullActSteps;

    /** 每步行动后的自检开关：1 开启（默认）/ 0 关闭（开启后每一步多一次调用，检查故事逻辑并修正叙述） */
    private String stepSelfcheck;
    /** 每步自检的模式：off 关闭 / suspicious 命中才查（默认）/ always 每步都查 */
    private String stepSelfcheckMode;
    /** 危险度 0（安全）地点的遭遇概率 %：默认 0 = 完全不刷遭遇 */
    private String encounterChance0;
}
