package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.GitalkReplyDTO;
import com.bc.bcblog.service.GitalkService;
import com.bc.bcblog.vo.GitalkCommentVO;
import com.bc.bcblog.vo.GitalkConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台 Gitalk 评论配置接口。 */
@RestController
@RequestMapping("/api/admin/gitalk")
@RequiredArgsConstructor
public class AdminGitalkController {

    private final GitalkService gitalkService;

    @GetMapping("/config")
    public Result<GitalkConfigVO> config() {
        return Result.ok(gitalkService.getConfig());
    }

    @PostMapping("/config")
    public Result<Void> save(@RequestBody GitalkConfigVO vo) {
        gitalkService.save(vo);
        return Result.ok();
    }

    /** 分页查询 Gitalk 评论 */
    @GetMapping("/comments")
    public Result<PageResult<GitalkCommentVO>> comments(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Long issueNumber) {
        return Result.ok(gitalkService.page(page, size, keyword, issueNumber));
    }

    /** 删除 Gitalk 评论 */
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        gitalkService.deleteComment(id);
        return Result.ok();
    }

    /** 回复某篇文章的 Gitalk 评论 */
    @PostMapping("/comments/reply")
    public Result<Void> reply(@RequestBody GitalkReplyDTO dto) {
        gitalkService.replyComment(dto.getIssueNumber(), dto.getBody());
        return Result.ok();
    }
}
