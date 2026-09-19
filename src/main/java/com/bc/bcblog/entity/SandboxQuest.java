package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅人委托板上的一条委托。
 *
 * 生命周期：open（可接）→ taken（接取中）→ completed（已完成）；
 * 角色放弃时回到 open 并把 progress 清零（放弃过的角色名记在 abandoned_by）。
 * 前台只展示"最新一批可接 + 所有接取中 + 近三天已完成"。
 */
@Data
@TableName("sandbox_quest")
public class SandboxQuest {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 所属批次（刷新时间）：可接的委托只展示最新一批 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime batchTime;
    /** 委托标题（角色按标题一字不差地接取） */
    private String title;
    /** 委托详情 */
    private String description;
    /** hunt 讨伐 / gather 采集 / escort 护送 / explore 探索 / chore 杂务 / other */
    private String questType;
    /** 难度 1~5 */
    private Integer difficulty;
    /** 目标地区（一级地点名） */
    private String locationName;
    /** 目标与数量（一句话） */
    private String target;
    /** 对手战斗力（讨伐类；服务端会夹到该地点的战力区间） */
    private Integer power;
    /** 奖励金币 */
    private Integer rewardCoins;
    /** 奖励物品（JSON 数组文本） */
    private String rewardItems;
    /** 完成进度 0~100 */
    private Integer progress;
    /** AI 最近一次对进度的判断 */
    private String progressNote;
    /** open / taken / completed */
    private String status;
    private Long takerId;
    private String takerName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime takenAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    /** 完成经过（一句话） */
    private String completionNote;
    /** 曾接取后放弃的角色名（顿号分隔） */
    private String abandonedBy;
    /** ai / admin */
    private String source;
    private Integer pinned;
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ---------------- 以下字段不落库，只在接口里用 ----------------

    /** 奖励物品列表（把 reward_items 解析出来给前端编辑） */
    @TableField(exist = false)
    private List<SandboxQuestReward> rewards;
    /** 目标地区离某个角色多少公里（前台与提示词里用；不传角色时为空） */
    @TableField(exist = false)
    private Double distanceKm;
    /** 这个角色曾经放弃过这条委托（提示词里点一句"你之前放弃过它"） */
    @TableField(exist = false)
    private Boolean abandonedByMe;
    /**
     * 这条委托此刻是不是真的在委托板上（由服务端按前台的同一套规则算出来，不落库）。
     * 规则：允许展示 + （接取中 / 近三天已完成 / 属于最新一批的可接）。
     * 后台列表用它代替原来那个"上板开关"——开关只表示"允许展示"，
     * 和"现在在不在板上"不是一回事，放在一起会让人以为被刷掉的委托还在板上。
     */
    @TableField(exist = false)
    private Boolean onBoard;
}
