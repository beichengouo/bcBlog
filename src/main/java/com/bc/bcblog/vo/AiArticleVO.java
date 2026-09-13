package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/** AI 生成文章返回对象。 */
@Data
public class AiArticleVO {
    private String title;
    private String summary;
    private String content;
    private List<String> tags;
    private String category;
}
