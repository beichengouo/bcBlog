package com.bc.bcblog.service;

import cn.hutool.json.JSONObject;
import com.bc.bcblog.common.PageResult;
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

    /** 后台分页查询评论，可按文章（issue）或关键词筛选。 */
    PageResult<GitalkCommentVO> page(int page, int size, String keyword, Long issueNumber);

    /** 删除一条 Gitalk 评论。 */
    void deleteComment(Long commentId);

    /** 在指定文章对应的 Issue 下回复评论。 */
    void replyComment(Long issueNumber, String body);
}
