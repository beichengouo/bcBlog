package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 等级配置。 */
@Data
@TableName("sys_level")
public class SysLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer level;
    private String name;
    /** 达到该等级所需经验 */
    private Integer expRequired;
    private String icon;
    private String color;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
