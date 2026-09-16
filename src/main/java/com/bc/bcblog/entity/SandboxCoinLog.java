package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙盒金币流水。
 * type：contribute 用户用积分贡献 / earn 角色赚取 / spend 角色消耗 / admin 管理员调整。
 */
@Data
@TableName("sandbox_coin_log")
public class SandboxCoinLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色 ID */
    private Long characterId;
    /** 所属世界（用于多世界隔离与级联清理） */
    private Long worldId;
    /** 贡献人用户 ID，AI 赚取/消耗时为空 */
    private Long userId;
    /** 贡献人昵称快照 */
    private String userName;
    /** 类型 */
    private String type;
    /** 金币变化，正为增加、负为减少 */
    private Integer coins;
    /** 贡献时消耗的积分 */
    private Integer pointsCost;
    /** 变化后余额 */
    private Integer balance;
    /** 说明 */
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
