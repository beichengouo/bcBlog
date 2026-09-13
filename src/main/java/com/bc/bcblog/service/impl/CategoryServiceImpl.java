package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogCategory;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCategoryMapper;
import com.bc.bcblog.service.CategoryService;
import com.bc.bcblog.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类管理实现：查询时把扁平数据组装成树形结构。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final BlogCategoryMapper categoryMapper;
    private final BlogArticleMapper articleMapper;

    @Override
    public List<CategoryVO> tree() {
        List<BlogCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<BlogCategory>()
                .orderByAsc(BlogCategory::getSort)
                .orderByAsc(BlogCategory::getId));

        // 先全部转成 VO，方便按 id 快速查找父节点
        Map<Long, CategoryVO> map = all.stream()
                .collect(Collectors.toMap(BlogCategory::getId, this::toVo));

        List<CategoryVO> roots = new ArrayList<>();
        for (BlogCategory c : all) {
            CategoryVO vo = map.get(c.getId());
            Long parentId = c.getParentId() == null ? 0L : c.getParentId();
            if (parentId == 0L) {
                roots.add(vo);
            } else {
                CategoryVO parent = map.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父节点不存在时，兜底按顶级处理，避免节点丢失
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    @Override
    public void save(BlogCategory category) {
        validate(category);
        if (category.getParentId() != 0L && categoryMapper.selectById(category.getParentId()) == null) {
            throw new BusinessException("父分类不存在");
        }
        category.setId(null);
        category.setCreateTime(LocalDateTime.now());
        categoryMapper.insert(category);
    }

    @Override
    public void update(BlogCategory category) {
        if (category.getId() == null) {
            throw new BusinessException("缺少分类ID");
        }
        validate(category);
        if (category.getParentId().equals(category.getId())) {
            throw new BusinessException("父分类不能是自身");
        }
        if (category.getParentId() != 0L && categoryMapper.selectById(category.getParentId()) == null) {
            throw new BusinessException("父分类不存在");
        }
        categoryMapper.updateById(category);
    }

    @Override
    public void delete(Long id) {
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException("存在子分类，无法删除");
        }
        Long articles = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getCategoryId, id));
        if (articles != null && articles > 0) {
            throw new BusinessException("该分类下存在文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    /** 校验并规范化分类字段 */
    private void validate(BlogCategory c) {
        if (c.getName() == null || c.getName().trim().isEmpty()) {
            throw new BusinessException("分类名不能为空");
        }
        c.setName(c.getName().trim());
        if (c.getSort() == null) {
            c.setSort(0);
        }
        if (c.getParentId() == null) {
            c.setParentId(0L);
        }
    }

    /** 实体转树形 VO */
    private CategoryVO toVo(BlogCategory c) {
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setParentId(c.getParentId());
        vo.setSort(c.getSort());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
