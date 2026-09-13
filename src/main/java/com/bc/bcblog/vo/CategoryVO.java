package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/**
 * 分类树形展示对象，children 存放子分类，供前端树形表格渲染。
 */
@Data
public class CategoryVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private List<CategoryVO> children;
}
