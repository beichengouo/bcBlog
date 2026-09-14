package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 签到记录。 */
@Data
@TableName("sys_sign_log")
public class SysSignLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate signDate;
    private Integer exp;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
