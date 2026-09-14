package com.bc.bcblog.service;

import cn.hutool.json.JSONObject;
import com.bc.bcblog.vo.GitalkCommentVO;
import com.bc.bcblog.vo.GitalkConfigVO;

import java.util.List;

/** Gitalk 评论服务：配置管理 + GitHub OAuth 代理。 */
public interface GitalkService {

    /** 读取 Gitalk 配置。 */
    GitalkConfigVO getConfig();

    /** 保存 Gitalk 配置。 */
    void save(GitalkConfigVO vo);

    /** 用授权 code 换取 GitHub access_token。 */
    JSONObject exchangeToken(String code);

    /** 拉取最近评论（GitHub Issues 评论），失败时返回空列表。 */
    List<GitalkCommentVO> recentComments(int limit);
}
