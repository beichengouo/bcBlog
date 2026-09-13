package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 页面背景壁纸（图片或视频）。 */
@Data
@TableName("background")
public class Background {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String url;
    private Integer portalActive;
    private Integer adminActive;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
