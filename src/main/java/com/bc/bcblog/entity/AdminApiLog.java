package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** API 调用审计：谁、什么时候、调用了什么、用了哪个服务商、成功与否。 */
@Data
@TableName("admin_api_log")
public class AdminApiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 调用者 ID，定时任务为空 */
    private Long adminId;
    /** 调用者账号快照 */
    private String adminName;
    /** 动作，如「沙盒·立即执行一次」 */
    private String action;
    /** manual 手动 / schedule 定时 */
    private String source;
    /** 使用的服务商或第三方接口（不含密钥） */
    private String target;
    /** 是否成功 */
    private Integer success;
    /** 失败原因 */
    private String message;
    /** 耗时毫秒 */
    private Integer costMs;
    /** 调用方 IP */
    private String ip;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
