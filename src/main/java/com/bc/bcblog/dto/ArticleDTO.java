package com.bc.bcblog.dto;

import lombok.Data;

import java.util.List;

/**
 * 文章新增/编辑入参，tagIds 用于维护文章与标签的关联。
 */
@Data
public class ArticleDTO {
    private Long id;
    private String title;
    private String summary;
    private String content;
    private String cover;
    private Long categoryId;
    private Integer status;
    private Integer isTop;
    private List<Long> tagIds;
}
