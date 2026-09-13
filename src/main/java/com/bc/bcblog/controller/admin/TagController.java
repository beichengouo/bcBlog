package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogTag;
import com.bc.bcblog.service.TagService;
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
 * 后台标签管理接口。
 */
@RestController
@RequestMapping("/api/admin/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 获取全部标签 */
    @GetMapping("/list")
    public Result<List<BlogTag>> list() {
        return Result.ok(tagService.list());
    }

    /** 新增标签 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody BlogTag tag) {
        tagService.save(tag);
        return Result.ok();
    }

    /** 修改标签 */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody BlogTag tag) {
        tagService.update(tag);
        return Result.ok();
    }

    /** 删除标签 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.ok();
    }
}
