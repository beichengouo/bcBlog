package com.bc.bcblog.vo;

import lombok.Data;

/** Gitalk（GitHub Issues）评论展示对象。 */
@Data
public class GitalkCommentVO {
    private Long id;
    private String author;
    private String avatar;
    private String body;
    /** GitHub 返回的 ISO 时间字符串 */
    private String createdAt;
    private String htmlUrl;
    private String pageTitle;
    /** 站内页面路径，例如 /portal/article/1 */
    private String pagePath;
    private Long issueNumber;
}
