package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 服务商配置，兼容 OpenAI 风格接口。 */
@Data
@TableName("ai_provider")
public class AiProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private Integer isDefault;
    /** 归属管理员 ID；null 表示系统服务商（超管维护，定时任务使用） */
    private Long ownerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
