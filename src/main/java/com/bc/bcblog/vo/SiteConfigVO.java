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
}
