package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.CommentDTO;
import com.bc.bcblog.dto.CommentStatusDTO;
import com.bc.bcblog.vo.AdminCommentVO;
import com.bc.bcblog.vo.CommentVO;

public interface CommentService {
    PageResult<CommentVO> pageByArticle(Long articleId, long page, long size);
    void save(CommentDTO dto);
    PageResult<AdminCommentVO> pageAdmin(long page, long size, Integer status);
    void delete(Long id);
    void updateStatus(CommentStatusDTO dto);
}
