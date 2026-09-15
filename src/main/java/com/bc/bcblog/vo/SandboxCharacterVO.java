package com.bc.bcblog.vo;

import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCoinLog;
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
    private Integer x;
    private Integer y;
    private String locationName;
    /** 当前状态（AI 生成的键值对，如 体力/魔力/心情） */
    private Map<String, Object> status;
    /** 金币余额 */
    private Integer coins;
    /** 最近几条金币流水（贡献 / 赚取 / 消耗） */
    private List<SandboxCoinLog> recentCoins;
    /** 与其他角色的好感度 */
    private List<SandboxRelationVO> relations;
    /** 最近几条行动，前台档案面板展示 */
    private List<SandboxAct> recentActs;
    /** 下次自动行动时间（前台展示倒计时用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRunTime;
    /** 上次行动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRunTime;
    /** 是否正在启用自动行动 */
    private Integer enabled;
}
