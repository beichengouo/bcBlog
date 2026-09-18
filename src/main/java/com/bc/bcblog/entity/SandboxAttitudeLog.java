package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒角色「态度变化」记录。
 *
 * 态度指的是角色对「实力」与「财富」的看法（sandbox_character.power_view / wealth_view）。
 * 它平时不变，只有当行动里真的发生了影响认知的事（受伤、惨败、破产、暴富、被救…）才可能微调，
 * 每次调整都会在这里留一条，前台角色档案展示最近几条，管理员也能据此回溯。
 */
@Data
@TableName("sandbox_attitude_log")
public class SandboxAttitudeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色 ID */
    private Long characterId;
    /** 角色名快照（角色删除后仍可追溯） */
    private String characterName;
    /** 触发这次变化的那条行动记录 ID */
    private Long actId;
    /** power=对实力的看法 / wealth=对财富的看法 */
    private String kind;
    /** 变化前的说法 */
    private String oldView;
    /** 变化后的说法 */
    private String newView;
    /** 为什么变（AI 给的一句话，前台展示用） */
    private String reason;
    /** 1 = 重大事件触发（可突破冷却） */
    private Integer major;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
