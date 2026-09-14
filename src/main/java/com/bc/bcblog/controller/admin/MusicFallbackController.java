package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.MusicFallback;
import com.bc.bcblog.service.MusicFallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台默认歌曲管理接口。 */
@RestController
@RequestMapping("/api/admin/music/fallback")
@RequiredArgsConstructor
public class MusicFallbackController {

    private final MusicFallbackService fallbackService;

    @GetMapping
    public Result<List<MusicFallback>> list() {
        return Result.ok(fallbackService.list());
    }

    @PostMapping
    public Result<Void> save(@RequestBody MusicFallback song) {
        fallbackService.save(song);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fallbackService.delete(id);
        return Result.ok();
    }
}
