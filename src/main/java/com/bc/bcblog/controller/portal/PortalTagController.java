package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.BlogTag;
import com.bc.bcblog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台标签接口（游客可访问）。
 */
@RestController
@RequestMapping("/api/portal/tag")
@RequiredArgsConstructor
public class PortalTagController {

    private final TagService tagService;

    @GetMapping("/list")
    public Result<List<BlogTag>> list() {
        return Result.ok(tagService.list());
    }
}
