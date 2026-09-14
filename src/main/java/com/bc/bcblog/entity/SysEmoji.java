package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 表情包。 */
@Data
@TableName("sys_emoji")
public class SysEmoji {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pack;
    private String name;
    private String url;
    private Integer sortOrder;
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
