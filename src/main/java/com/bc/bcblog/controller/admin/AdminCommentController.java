package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.CommentStatusDTO;
import com.bc.bcblog.service.CommentService;
import com.bc.bcblog.vo.AdminCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台评论管理接口。
 */
@RestController
@RequestMapping("/api/admin/comment")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    /** 分页查询评论（可按状态筛选） */
    @GetMapping("/page")
    public Result<PageResult<AdminCommentVO>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Integer status) {
        return Result.ok(commentService.pageAdmin(page, size, status));
    }

    /** 修改评论状态（通过/拒绝） */
    @PutMapping("/status")
    public Result<Void> status(@RequestBody CommentStatusDTO dto) {
        commentService.updateStatus(dto);
        return Result.ok();
    }

    /** 删除评论 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.ok();
    }
}
