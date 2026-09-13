package com.bc.bcblog.service;

import com.bc.bcblog.entity.BlogTag;

import java.util.List;

/** 标签管理服务 */
public interface TagService {
    /** 查询全部标签 */
    List<BlogTag> list();

    /** 新增标签 */
    void save(BlogTag tag);

    /** 修改标签 */
    void update(BlogTag tag);

    /** 删除标签 */
    void delete(Long id);
}
