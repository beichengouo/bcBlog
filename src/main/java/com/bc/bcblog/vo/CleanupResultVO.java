package com.bc.bcblog.vo;

import lombok.Data;

/** 定期清理结果。 */
@Data
public class CleanupResultVO {
    private Integer loginLog;
    private Integer visitStat;
    private Integer signLog;
    private Integer pointLog;
}
