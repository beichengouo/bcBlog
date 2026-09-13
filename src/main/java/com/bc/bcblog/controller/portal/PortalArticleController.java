package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.service.ArticleService;
import com.bc.bcblog.vo.PortalArticleDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/article")
@RequiredArgsConstructor
public class PortalArticleController {

    private final ArticleService articleService;

    @GetMapping("/list")
    public Result<PageResult<BlogArticle>> list(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Long tagId,
                                                @RequestParam(required = false) String keyword) {
        return Result.ok(articleService.pagePublished(page, size, categoryId, tagId, keyword));
    }

    @GetMapping("/{id}")
    public Result<PortalArticleDetailVO> detail(@PathVariable Long id) {
        return Result.ok(articleService.portalDetail(id));
    }
}
