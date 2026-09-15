package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 旅人纪闻：当天世界里发生的大事。
 * 由 AI 独立生成（管理员也可自行添加），前台在地图左上角展示，仅显示当天。
 */
@Data
@TableName("sandbox_news")
public class SandboxNews {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 一句话事件，前台展示 */
    private String title;
    /** 补充说明 */
    private String content;
    /** 事件发生地点 */
    private String locationName;
    /** 事件坐标 X，用于计算与角色的距离 */
    private Integer x;
    /** 事件坐标 Y */
    private Integer y;
    /** 重要度：1 普通 / 2 重要 / 3 重大 */
    private Integer level;
    /** 来源：ai 自动生成 / admin 管理员添加 */
    private String source;
    /** 归属日期，只展示当天 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate newsDate;
    /** 是否置顶 */
    private Integer pinned;
    /** 是否启用 */
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
