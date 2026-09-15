package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒角色好感度。
 * 有方向：characterId 对 targetId 的好感度（A 对 B 与 B 对 A 是两条记录）。
 */
@Data
@TableName("sandbox_relation")
public class SandboxRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 好感度的持有方角色 ID */
    private Long characterId;
    /** 对象角色 ID */
    private Long targetId;
    /** 好感度 -100~100 */
    private Integer favor;
    /** 上一次实际生效的好感度变化（被上限截断后为实际值） */
    private Integer lastChange;
    /** 上一次好感度变化时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastChangeTime;
    /** 管理员备注 */
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
