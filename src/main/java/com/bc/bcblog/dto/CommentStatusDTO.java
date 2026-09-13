package com.bc.bcblog.dto;

import lombok.Data;

/**
 * 后台修改评论状态入参。
 */
@Data
public class CommentStatusDTO {
    private Long id;
    private Integer status;
}
