package com.bc.bcblog.service;

import com.bc.bcblog.entity.BlogCategory;
import com.bc.bcblog.vo.CategoryVO;

import java.util.List;

/** 分类管理服务 */
public interface CategoryService {
    /** 查询分类树 */
    List<CategoryVO> tree();

    /** 新增分类 */
    void save(BlogCategory category);

    /** 修改分类 */
    void update(BlogCategory category);

    /** 删除分类 */
    void delete(Long id);
}
