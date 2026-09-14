package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/** 每日访问量统计。 */
@Data
@TableName("sys_visit_stat")
public class SysVisitStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate statDate;
    private Long pv;
}
