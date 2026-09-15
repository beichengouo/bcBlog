package com.bc.bcblog.vo;

import lombok.Data;

/** 定期清理结果。 */
@Data
public class CleanupResultVO {
    private Integer loginLog;
    private Integer visitStat;
    private Integer signLog;
    private Integer pointLog;
    /** 清理掉的沙盒行动日志条数 */
    private Integer sandboxAct;
    /** 清理掉的沙盒记忆条数 */
    private Integer sandboxMemory;
    /** 清理掉的旅人纪闻条数 */
    private Integer sandboxNews;
    /** 清理掉的 API 调用审计条数 */
    private Integer adminApiLog;
}
