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
    /** 旅人集市：清理掉的旧批次商品数 */
    private Integer sandboxShopItem;
    /** 旅人委托板：清理掉的已完成与旧批次委托数 */
    private Integer sandboxQuest;
    /** 清理掉的 API 调用审计条数 */
    private Integer adminApiLog;
}
