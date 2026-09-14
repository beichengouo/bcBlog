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
}
