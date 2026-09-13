package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台评论管理对象，附带文章标题，便于管理员识别。
 */
@Data
public class AdminCommentVO {
    private Long id;
    private Long articleId;
    private String articleTitle;
    private String nickname;
    private String email;
    private String content;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
