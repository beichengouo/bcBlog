package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前台评论展示对象，不含邮箱等隐私字段。
 */
@Data
public class CommentVO {
    private Long id;
    private Long articleId;
    private Long userId;
    private String articleTitle;
    private String nickname;
    private String avatar;
    private Integer level;
    private String levelName;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
