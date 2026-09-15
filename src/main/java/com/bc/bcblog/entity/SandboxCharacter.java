package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 沙盒角色实体。
 * 人设写法参考酒馆角色卡：人设 + 世界书 + 状态 + 记忆，一起拼成提示词交给 AI。
 */
@Data
@TableName("sandbox_character")
public class SandboxCharacter {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色名 */
    private String name;
    /** 称号 */
    private String title;
    /** 头像 / 立绘地址 */
    private String avatar;
    /** 外貌描述，前台展示 */
    private String appearance;
    /** 人设提示词 */
    private String persona;
    /** 绑定的 AI 服务商 ID */
    private Long providerId;
    /** 使用的模型 */
    private String model;
    /** 采样温度 */
    private BigDecimal temperature;
    /** 当前横向坐标百分比 */
    private Integer x;
    /** 当前纵向坐标百分比 */
    private Integer y;
    /** 当前位置名称 */
    private String locationName;
    /** 当前所在的二级地点 */
    private String subLocation;
    /** 当前状态（JSON 字符串，内容由 AI 生成） */
    private String statusJson;
    /** 金币余额：AI 日常活动赚取或消耗，前台用户也可用积分贡献 */
    private Integer coins;
    /** 下次 AI 行动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRunTime;
    /** 下次行动的原因，如「睡觉」，前台展示用 */
    private String nextReason;
    /** 上次 AI 行动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRunTime;
    /** 行动间隔最小值（分钟） */
    private Integer intervalMin;
    /** 行动间隔最大值（分钟） */
    private Integer intervalMax;
    /** 该角色 AI 间隔下限（分钟），留空用全局设置 */
    private Integer aiIntervalMin;
    /** 该角色 AI 间隔上限（分钟），留空用全局设置 */
    private Integer aiIntervalMax;
    /** 最后一次调用失败原因 */
    private String lastError;
    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
