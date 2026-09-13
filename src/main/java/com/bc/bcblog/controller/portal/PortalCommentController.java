package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.CommentDTO;
import com.bc.bcblog.service.CommentService;
import com.bc.bcblog.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台评论接口（游客可访问）。
 */
@RestController
@RequestMapping("/api/portal/comment")
@RequiredArgsConstructor
public class PortalCommentController {

    private final CommentService commentService;

    /** 获取某篇文章已通过的评论 */
    @GetMapping("/list")
    public Result<PageResult<CommentVO>> list(@RequestParam Long articleId,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(commentService.pageByArticle(articleId, page, size));
    }

    /** 游客发表评论 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody CommentDTO dto) {
        commentService.save(dto);
        return Result.ok();
    }
}
