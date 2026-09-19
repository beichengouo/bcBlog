package com.bc.bcblog.vo;

import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxAttitudeLog;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxMemory;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 前台沙盒角色展示对象。 */
@Data
public class SandboxCharacterVO {
    private Long id;
    private String name;
    private String title;
    private String avatar;
    private String appearance;
    /** 此刻的样子：穿着、干净程度、伤势外观、发型神态（与 appearance 的"底子"分开） */
    private String currentLook;
    private Integer x;
    private Integer y;
    private String locationName;
    /** 当前所在的二级地点 */
    private String subLocation;
    /** 当前状态（AI 生成的键值对，如 体力/魔力/心情） */
    private Map<String, Object> status;
    /** 金币余额 */
    private Integer coins;
    /** 战斗力：综合实力（战斗技巧、魔力、装备），默认 10 */
    private Integer combatPower;
    /** 当前目标（AI 维护，管理员可改） */
    private String goal;
    /** 最近几条金币流水（贡献 / 赚取 / 消耗） */
    private List<SandboxCoinLog> recentCoins;
    /** 与其他角色的好感度 */
    private List<SandboxRelationVO> relations;
    /** 背包物品 */
    private List<SandboxItem> items;
    /** 最近的每日记忆（前台可展示「回忆」） */
    private List<SandboxMemory> recentMemories;
    /** 最近几条行动，前台档案面板展示 */
    private List<SandboxAct> recentActs;
    /** 对自身实力的看法（后台可改，前台档案可展示） */
    private String powerView;
    /** 对金钱财富的看法 */
    private String wealthView;
    /** 最近几次「想法变化」：角色怎么看待实力与财富会随经历缓慢改变，前台展示成长轨迹 */
    private List<SandboxAttitudeLog> recentAttitudes;
    /** 下次自动行动时间（前台展示倒计时用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRunTime;
    /** 上次行动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRunTime;
    /** 下次行动原因，如「睡觉」 */
    private String nextReason;
    /** 是否正在启用自动行动 */
    private Integer enabled;
    /** 当前正在执行的委托（没有进行中的委托时为 null） */
    private com.bc.bcblog.entity.SandboxQuest currentQuest;
    /** 最近完成的委托（近三天，前台展示"TA 最近完成了什么"，与委托板共用一份数据） */
    private List<com.bc.bcblog.entity.SandboxQuest> recentQuests;
}
