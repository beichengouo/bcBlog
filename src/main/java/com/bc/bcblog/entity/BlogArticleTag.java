package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章与标签的关联实体（多对多中间表）。
 */
@Data
@TableName("blog_article_tag")
public class BlogArticleTag {
    /** 文章ID */
    private Long articleId;

    /** 标签ID */
    private Long tagId;
}
