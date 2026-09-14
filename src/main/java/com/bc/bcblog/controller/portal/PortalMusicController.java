package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.MusicFallback;
import com.bc.bcblog.service.MusicFallbackService;
import com.bc.bcblog.service.MusicPlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台音乐接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/music")
@RequiredArgsConstructor
public class PortalMusicController {

    private final MusicPlaylistService playlistService;
    private final MusicFallbackService fallbackService;

    @GetMapping("/active")
    public Result<String> active() {
        return Result.ok(playlistService.activePlaylistId());
    }

    /** 歌单加载失败时使用的默认歌曲 */
    @GetMapping("/fallback")
    public Result<List<MusicFallback>> fallback() {
        return Result.ok(fallbackService.list());
    }
}
