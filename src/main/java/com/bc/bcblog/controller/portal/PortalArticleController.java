package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.service.ArticleService;
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
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.ok(articleService.pagePublished(page, size));
    }

    @GetMapping("/{id}")
    public Result<BlogArticle> detail(@PathVariable Long id) {
        return Result.ok(articleService.detail(id));
    }
}
