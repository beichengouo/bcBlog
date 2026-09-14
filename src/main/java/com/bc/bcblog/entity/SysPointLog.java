package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分流水。 */
@Data
@TableName("sys_point_log")
public class SysPointLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 类型，如 sign、comment、admin */
    private String type;
    /** 本次变化，可为负 */
    private Integer points;
    /** 变化后余额 */
    private Integer balance;
    private String reason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
