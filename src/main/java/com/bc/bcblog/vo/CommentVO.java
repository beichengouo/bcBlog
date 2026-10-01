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
    /** 是否由网站AI（IRIS）生成：1 是，前台评论昵称旁显示 AI 角标 */
    private Integer aiGenerated;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
