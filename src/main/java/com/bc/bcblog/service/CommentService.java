package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.CommentDTO;
import com.bc.bcblog.dto.CommentStatusDTO;
import com.bc.bcblog.vo.AdminCommentVO;
import com.bc.bcblog.vo.CommentVO;

import java.util.List;

public interface CommentService {
    PageResult<CommentVO> pageByArticle(Long articleId, long page, long size);

    /**
     * 首页「最近评论」用的最新已通过评论（跨文章）。
     * 原生评论模式下前台读这个，Gitalk 模式下读 Gitalk 的接口。
     */
    List<CommentVO> recentForPortal(int limit);
    void save(CommentDTO dto);
    PageResult<AdminCommentVO> pageAdmin(long page, long size, Integer status);
    void delete(Long id);
    void updateStatus(CommentStatusDTO dto);
}
