package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.CategoryService;
import com.bc.bcblog.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台分类接口（游客可访问）。
 */
@RestController
@RequestMapping("/api/portal/category")
@RequiredArgsConstructor
public class PortalCategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }
}
