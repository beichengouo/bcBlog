package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogCategory;
import com.bc.bcblog.service.CategoryService;
import com.bc.bcblog.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台分类管理接口。
 */
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 获取分类树 */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** 新增分类 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody BlogCategory category) {
        categoryService.save(category);
        return Result.ok();
    }

    /** 修改分类 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody BlogCategory category) {
        categoryService.update(category);
        return Result.ok();
    }

    /** 删除分类 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
