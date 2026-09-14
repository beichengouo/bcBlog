package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/** 仪表盘统计数据。 */
@Data
public class DashboardStatsVO {
    // 文章
    private Long articleCount;
    private Long publishedCount;
    private Long draftCount;
    // 分类 / 标签
    private Long categoryCount;
    private Long tagCount;
    // 评论（旧的本地评论 + Gitalk 最近评论列表）
    private Long commentCount;
    private List<GitalkCommentVO> recentComments;
    // 运行数据
    private Long viewCount;
    private Long photoCount;
    private Long resourceCount;
    private Long adminCount;
    private Long uptimeSeconds;
    private String startTime;
    /** 访问量统计 */
    private VisitStatsVO visitStats;
    // 文章列表
    private List<ArticleBrief> topArticles;
    private List<ArticleBrief> recentArticles;

    @Data
    public static class ArticleBrief {
        private Long id;
        private String title;
        private String cover;
        private Long viewCount;
        private String createTime;
    }
}
