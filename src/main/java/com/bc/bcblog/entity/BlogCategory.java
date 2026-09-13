package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客分类实体，支持多级树形分类。
 * parentId 为 0 表示顶级分类，其余指向父分类 id。
 */
@Data
@TableName("blog_category")
public class BlogCategory {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 父分类 id，0 表示顶级 */
    private Long parentId;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
