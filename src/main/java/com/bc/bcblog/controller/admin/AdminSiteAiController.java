package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SiteAiActivity;
import com.bc.bcblog.entity.SiteAiProfile;
import com.bc.bcblog.service.SiteAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 后台「网站AI」管理接口（整体为超管专属，见 AdminPathRules） */
@RestController
@RequestMapping("/api/admin/site-ai")
@RequiredArgsConstructor
public class AdminSiteAiController {

    private final SiteAiService siteAiService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(siteAiService.overview());
    }

    @GetMapping("/profile")
    public Result<SiteAiProfile> profile() {
        return Result.ok(siteAiService.profile());
    }

    @PutMapping("/profile")
    public Result<SiteAiProfile> saveProfile(@RequestBody SiteAiProfile form) {
        return Result.ok(siteAiService.saveProfile(form));
    }

    @GetMapping("/activities")
    public Result<PageResult<SiteAiActivity>> activities(@RequestParam(required = false) String type,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.ok(siteAiService.activityPage(type, page, size));
    }

    /** 立即执行一次：type = article / comment / status */
    @PostMapping("/run")
    public Result<SiteAiActivity> run(@RequestParam String type) {
        return Result.ok(siteAiService.run(type, true));
    }

    /** 一键撤销：文章下架 / 评论与回复删除 */
    @PostMapping("/activities/{id}/revert")
    public Result<Void> revert(@PathVariable Long id) {
        siteAiService.revert(id);
        return Result.ok();
    }

    /** 手动补生成今天的记忆 */
    @PostMapping("/memory")
    public Result<Integer> memory() {
        return Result.ok(siteAiService.summarize());
    }

    /** 把已发布文章的封面统一换成当前设置（管理员改完封面后一键替换） */
    @PostMapping("/covers/apply")
    public Result<Integer> applyCovers() {
        return Result.ok(siteAiService.applyCoverToExisting());
    }

    /** 预览"今日运行简报"：看看她写文章时能读到哪些数字 */
    @GetMapping("/digest")
    public Result<String> digest() {
        return Result.ok(siteAiService.digestPreview());
    }

    /** 重置今日调用计数（测试用：不删日志，只把计数起点推到现在） */
    @PostMapping("/counters/reset")
    public Result<Integer> resetCounters() {
        return Result.ok(siteAiService.resetTodayCounters());
    }
}
