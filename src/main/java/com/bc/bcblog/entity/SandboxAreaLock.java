package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒「地区执行锁」。
 *
 * 一个角色开始行动前先锁住自己所在的一级地区，防止同一片地区里两个角色同时行动、
 * 又刚好互相触发互动（那会造成行动双写、状态互相覆盖）。
 * 不同地区之间互不影响，可以并行执行。
 */
@Data
@TableName("sandbox_area_lock")
public class SandboxAreaLock {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 一级地区名（地点名） */
    private String areaName;
    /** 当前持有者（角色名，便于排查卡锁） */
    private String holder;
    /** 最近一次抢锁/续期时间 */
    private LocalDateTime lockedAt;
}
