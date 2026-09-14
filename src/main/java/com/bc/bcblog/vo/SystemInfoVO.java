package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统运行信息，用于前台底部展示稳定运行时间和当前时间。 */
@Data
public class SystemInfoVO {

    /** 后端启动时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 已运行秒数 */
    private Long uptimeSeconds;

    /** 服务器当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serverTime;
}
