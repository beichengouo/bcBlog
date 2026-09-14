package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/** Gitalk 评论配置。 */
@Data
public class GitalkConfigVO {
    private String clientId;
    private String clientSecret;
    private String repo;
    private String owner;
    private List<String> admin;
    /** 后台管理评论用的 GitHub Fine-grained Token，仅后台接口返回 */
    private String token;
}
