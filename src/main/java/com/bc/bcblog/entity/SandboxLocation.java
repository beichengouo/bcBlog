package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒地图地点。
 * 坐标使用百分比（0~100），这样换任何尺寸的地图背景图都不用重新标点。
 */
@Data
@TableName("sandbox_location")
public class SandboxLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 地点名称 */
    private String name;
    /** 地点图标：内置图标 key（如 forest）或上传的图片地址 */
    private String icon;
    /** 横向坐标百分比 0~100 */
    private Integer x;
    /** 纵向坐标百分比 0~100 */
    private Integer y;
    /** 区域宽度百分比，0 表示单点 */
    private Integer width;
    /** 区域高度百分比，0 表示单点 */
    private Integer height;
    /**
     * 多边形区域顶点 JSON：[[x,y],[x,y],...]，百分比坐标（0~100）。
     * 为空时按矩形区域（x/y/width/height）判定，老数据无需迁移。
     */
    private String polygon;
    /** 地点描述，会作为 AI 行动参考 */
    private String description;
    /** 排序，越小越靠前 */
    private Integer sortOrder;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
