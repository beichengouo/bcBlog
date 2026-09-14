package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 智库资源条目。 */
@Data
@TableName("blog_resource")
public class BlogResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String url;
    /** 网盘提取密码，可为空 */
    private String password;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
