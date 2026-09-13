package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.MusicPlaylist;
import com.bc.bcblog.service.MusicPlaylistService;
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

/** 后台音乐歌单管理接口。 */
@RestController
@RequestMapping("/api/admin/music/playlist")
@RequiredArgsConstructor
public class MusicPlaylistController {

    private final MusicPlaylistService playlistService;

    @GetMapping
    public Result<List<MusicPlaylist>> list() {
        return Result.ok(playlistService.list());
    }

    @PostMapping
    public Result<Void> add(@RequestBody MusicPlaylist playlist) {
        playlistService.add(playlist.getName(), playlist.getPlaylistId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        playlistService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/active")
    public Result<Void> setActive(@PathVariable Long id) {
        playlistService.setActive(id);
        return Result.ok();
    }
}
