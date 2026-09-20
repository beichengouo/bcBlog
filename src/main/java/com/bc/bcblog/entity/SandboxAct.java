package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 沙盒行动记录：角色每一步做了什么、想了什么。 */
@Data
@TableName("sandbox_act")
public class SandboxAct {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色 ID */
    private Long characterId;
    /** 这一步所处地点 */
    private String locationName;
    /** 这一步的二级地点（AI 自行创作，如「东侧集市」） */
    private String subLocation;
    /** 这一步的横向坐标 */
    private Integer x;
    /** 这一步的纵向坐标 */
    private Integer y;
    /**
     * 这一步相比上一步移动的实际距离（km），换了地点才有值。
     * 由服务端按页算好（分页后"上一条"不一定在同一页里，前端拿不到），只用于展示，不落库。
     */
    @TableField(exist = false)
    private Double moveKm;
    /** 做了什么，多条用换行分隔 */
    private String actions;
    /** 心声 */
    private String innerVoice;
    /** 这一步互动的其他角色，逗号分隔 */
    private String companions;
    /** 这一步的好感度变化，如「零 +3」 */
    private String favorChange;
    /** 这一步的物品变化，如「获得 干粮 +1」 */
    private String itemChange;
    /** 这一步结束后的状态（JSON） */
    private String statusJson;
    /** 这一步的金币变化，正为赚取、负为消耗 */
    private Integer coinChange;
    /** 这一步战斗力的变化，0 表示没变（前台只在变化时展示） */
    private Integer combatChange;
    /** 一句话概括 */
    private String summary;
    /** 这一步之后 AI 期望的间隔分钟数，0 表示未指定 */
    private Integer nextAfterMinutes;
    /** 间隔原因，如「睡觉」 */
    private String nextAfterReason;
    /** 这一步参考/听说的旅人纪闻标题 */
    private String newsRef;
    /** 本步由服务端注入的遭遇（危险地区掷骰命中时才会有），前台/后台可展示「他到底遇上了什么」 */
    private String encounter;
    /** 本步遭遇的对手名字/种类（AI 判断，例如「雾隐豹」）；前台把 encounter 里的战力与它拼在一起显示 */
    private String encounterFoe;
    /** 本步运气值：-3 大凶 ~ +3 大吉（服务端生成，前台展示） */
    private Integer luck;
    /** 这一步结束时角色的样子（外貌底子不变，随行动变化的是穿着、脏污、伤势外观这些） */
    private String look;
    /** 这一步和旅人委托有关时记一句，如「完成委托：清除晨雾森林的影狼」（前台据此显示徽章） */
    private String questEvent;
    /** AI 原始返回，便于排查问题 */
    private String rawResponse;
    /** 是否来自 AI：1 是，0 为兜底记录 */
    private Integer fromAi;
    /** 是否管理员手动执行：1 是（不占用每日额度） */
    private Integer manual;
    /** 是否由其他角色的互动触发的回应回合：1 是 */
    private Integer reaction;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
