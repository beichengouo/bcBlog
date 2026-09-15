package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理员登录 IP 记录：用于判断陌生 IP / 异地登录，并做通知去重。 */
@Data
@TableName("sys_login_ip")
public class SysLoginIp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String ip;
    /** IP 归属地（仅异常时查询并缓存） */
    private String region;
    private Integer loginCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;
    /** 最近一次异常通知时间，避免同一 IP 反复发信 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastNotifyTime;
}
