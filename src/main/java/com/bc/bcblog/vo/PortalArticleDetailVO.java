package com.bc.bcblog.vo;

import com.bc.bcblog.entity.BlogArticle;
import lombok.Data;

/**
 * 前台文章详情返回对象，附带上一篇/下一篇的简要信息。
 */
@Data
public class PortalArticleDetailVO {
    private BlogArticle article;
    private ArticleBrief prev;
    private ArticleBrief next;

    @Data
    public static class ArticleBrief {
        private Long id;
        private String title;
    }
}
