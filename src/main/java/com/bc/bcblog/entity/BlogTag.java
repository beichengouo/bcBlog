package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客标签实体，标签为扁平结构，不区分层级。
 */
@Data
@TableName("blog_tag")
public class BlogTag {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称，需唯一 */
    private String name;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
