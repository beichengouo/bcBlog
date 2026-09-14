package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 邮件模板。 */
@Data
@TableName("sys_email_template")
public class EmailTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 场景编码，如 register_code */
    private String scenario;
    private String name;
    private String subject;
    /** 背景图片地址 */
    private String backgroundImage;
    /** 内容卡片背景透明度 0.1 ~ 1 */
    private BigDecimal overlayOpacity;
    /** 正文 HTML，支持 {{变量}} */
    private String contentHtml;
    /** 可用变量说明 */
    private String variables;
    private Integer enabled;
    /** 是否为该场景当前启用的模板 */
    private Integer active;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
