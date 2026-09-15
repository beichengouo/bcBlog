package com.bc.bcblog.vo;

import lombok.Data;

/**
 * 站点设置对象。
 */
@Data
public class SiteConfigVO {
    private String siteName;
    private String siteLogo;
    private String siteIcp;
    private String siteDescription;
    private String siteKeywords;
    private String siteSlogan;
    private String weatherCity;
    private String hitokotoCategories;
    private Integer live2dEnabled;
    /** 首页中段文章轮播是否显示 */
    private Integer homeCarouselEnabled;
    /** 首页中段文章轮播显示数量 */
    private Integer homeCarouselCount;
    /** 评论系统：gitalk / native */
    private String commentSystem;
    /** 注册是否需要邀请码 */
    private Integer registerInviteRequired;
    /** 注册是否需要邮箱验证码 */
    private Integer registerEmailVerify;
    /** 签到经验 */
    private Integer signExp;
    /** 评论经验 */
    private Integer commentExp;
    /** 每天获得经验的评论次数上限 */
    private Integer commentExpLimit;
    /** 是否启用定期清理 */
    private Integer cleanupEnabled;
    /** 定期清理执行时间 HH:mm */
    private String cleanupTime;
    private Integer cleanupLoginLogDays;
    private Integer cleanupVisitStatDays;
    private Integer cleanupSignLogDays;
    private Integer cleanupPointLogDays;
    /** 沙盒行动日志保留天数 */
    private Integer cleanupSandboxActDays;
    /** 沙盒每日记忆保留天数 */
    private Integer cleanupSandboxMemoryDays;
    /** 旅人纪闻保留天数（1 = 只留当天） */
    private Integer cleanupSandboxNewsDays;
    /** API 调用审计保留天数 */
    private Integer cleanupAdminApiLogDays;
}
