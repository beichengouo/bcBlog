package com.bc.bcblog.vo;

import lombok.Data;

/**
 * 仪表盘统计数据。
 */
@Data
public class DashboardStatsVO {
    private Long articleCount;
    private Long categoryCount;
    private Long tagCount;
    private Long commentCount;
    private Long viewCount;
}
