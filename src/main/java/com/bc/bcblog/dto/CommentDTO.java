package com.bc.bcblog.dto;

import lombok.Data;

/**
 * 游客发表评论入参。
 */
@Data
public class CommentDTO {
    private Long articleId;
    private String nickname;
    private String email;
    private String content;
}
