package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.ArticleDTO;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/article")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @GetMapping("/page")
    public Result<PageResult<BlogArticle>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword) {
        return Result.ok(articleService.pageAdmin(page, size, keyword));
    }

    /** 获取文章编辑数据 */
    @GetMapping("/detail/{id}")
    public Result<ArticleDTO> detail(@PathVariable Long id) {
        return Result.ok(articleService.getForEdit(id));
    }

    /** 新增文章 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody ArticleDTO dto) {
        articleService.save(dto);
        return Result.ok();
    }

    /** 修改文章 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody ArticleDTO dto) {
        articleService.update(dto);
        return Result.ok();
    }

    /** 删除文章 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.ok();
    }
}
