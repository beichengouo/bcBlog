package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 前台单个页面的背景设置（首页 / 流光忆庭 / 智库 / 沙盒世界）。
 * 壁纸本身仍然存在共用的 background 表里，这里只记录「这个页面用哪张、透明度多少」。
 */
@Data
@TableName("page_background")
public class PageBackground {

    /** 页面标识：home / photos / resources / sandbox */
    @TableId(type = IdType.INPUT)
    private String pageKey;
    /** follow = 跟随前台默认壁纸；none = 不使用壁纸；custom = 使用 backgroundId */
    private String mode;
    /** mode = custom 时使用的壁纸 id */
    private Long backgroundId;
    /** 壁纸不透明度 0.10~1.00 */
    private BigDecimal opacity;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
