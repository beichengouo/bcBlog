package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 沙盒角色每日记忆。
 * 每天晚上把当天的行动总结成一段长期记忆，后续几天的提示词只带记忆 + 近期行动。
 */
@Data
@TableName("sandbox_memory")
public class SandboxMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色 ID */
    private Long characterId;
    /** 记忆对应的日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate memoryDate;
    /** 当天的记忆总结 */
    private String summary;
    /** 当天行动条数 */
    private Integer actCount;
    /** 是否由 AI 总结：1 是，0 为兜底拼接 */
    private Integer fromAi;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
