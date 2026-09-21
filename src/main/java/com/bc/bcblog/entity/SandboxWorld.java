package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒世界实体。
 * 一个世界对应一张地图，地图背景图、世界观设定都由管理员在后台维护。
 */
@Data
@TableName("sandbox_world")
public class SandboxWorld {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 世界名称 */
    private String name;
    /** 世界简介，前台展示 */
    private String description;
    /** 地图背景图地址 */
    private String mapImage;
    /** 世界设定：写给 AI 的世界观、规则与文风 */
    private String worldPrompt;
    /**
     * 「魔力」这条资源条在本世界叫什么：剑与魔法填「魔力」、修仙填「灵力」、现代填「精力」。
     * 留空表示这个世界没有这条属性（提示词不再要求输出、前台也不再显示）。
     * 没配过（null）时按默认的「魔力」处理，避免老世界突然少一条。
     */
    private String manaLabel;
    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;
    /** 前台是否可见：1 出现在前台世界下拉（可以只看历史），0 完全隐藏 */
    private Integer portalVisible;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
