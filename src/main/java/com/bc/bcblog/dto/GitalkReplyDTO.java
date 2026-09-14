package com.bc.bcblog.dto;

import lombok.Data;

/** 后台回复 Gitalk 评论入参。 */
@Data
public class GitalkReplyDTO {
    private Long issueNumber;
    private String body;
}
