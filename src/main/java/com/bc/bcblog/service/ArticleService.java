package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.ArticleDTO;
import com.bc.bcblog.entity.BlogArticle;

public interface ArticleService {
    PageResult<BlogArticle> pagePublished(long page, long size);
    BlogArticle detail(Long id);
    PageResult<BlogArticle> pageAdmin(long page, long size, String keyword);
    /** 新增文章 */
    void save(ArticleDTO dto);
    /** 修改文章 */
    void update(ArticleDTO dto);
    /** 删除文章 */
    void delete(Long id);
    /** 获取文章编辑数据（含标签ID列表） */
    ArticleDTO getForEdit(Long id);
}
