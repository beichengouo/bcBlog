package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IRIS 的一次活动记录（写文章 / 评论 / 回复 / 状态更新 / 每日记忆）。
 *
 * 保留模型、提示词版本与原始输出的原因：她发的内容是直接对外发布的，
 * 万一哪天内容不对劲，要能立刻定位"哪一次调用、哪个模型、什么输出"。
 */
@Data
@TableName("site_ai_activity")
public class SiteAiActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aiName;
    /** article / comment / reply / status / memory */
    private String activityType;
    private String targetType;
    private Long targetId;
    private String title;
    private String content;
    private String model;
    private Long providerId;
    private String promptVersion;
    private String rawResponse;
    /** success / failed / skipped / blocked */
    private String status;
    private String error;
    private Integer revertible;
    private Integer reverted;
    private Integer costMs;
    private LocalDateTime createTime;
}
