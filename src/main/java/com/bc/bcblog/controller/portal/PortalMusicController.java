package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.MusicPlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前台音乐接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/music")
@RequiredArgsConstructor
public class PortalMusicController {

    private final MusicPlaylistService playlistService;

    @GetMapping("/active")
    public Result<String> active() {
        return Result.ok(playlistService.activePlaylistId());
    }
}
