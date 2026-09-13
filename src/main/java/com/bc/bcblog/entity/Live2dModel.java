package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Live2D 看板娘模型实体。
 * 管理员可维护模型库并切换前台展示的模型，前台游客无需登录即可读取。
 */
@Data
@TableName("live2d_model")
public class Live2dModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模型唯一标识，用于避免重复导入 */
    private String modelKey;
    /** 中文名称，后台用于清晰标注角色 */
    private String name;
    /** 角色说明 */
    private String description;
    /** 模型 model.json 完整地址 */
    private String url;
    /** 排序值，越小越靠前 */
    private Integer sortOrder;
    /** 是否当前展示：1 是，0 否 */
    private Integer active;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
