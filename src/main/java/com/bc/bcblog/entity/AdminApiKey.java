package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理员个人密钥（如 DeepSeek API Key）。 */
@Data
@TableName("admin_api_key")
public class AdminApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属管理员 */
    private Long adminId;
    /** 密钥名称，如 deepseek_api_key */
    private String keyName;
    /** 密钥值（程序加密后存储） */
    private String keyValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
