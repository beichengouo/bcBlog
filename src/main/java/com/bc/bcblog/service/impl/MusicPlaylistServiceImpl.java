package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.MusicPlaylist;
import com.bc.bcblog.mapper.MusicPlaylistMapper;
import com.bc.bcblog.service.MusicPlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 音乐歌单服务。 */
@Service
@RequiredArgsConstructor
public class MusicPlaylistServiceImpl implements MusicPlaylistService {

    private final MusicPlaylistMapper playlistMapper;

    @Override
    public List<MusicPlaylist> list() {
        return playlistMapper.selectList(new LambdaQueryWrapper<MusicPlaylist>()
                .orderByDesc(MusicPlaylist::getActive)
                .orderByAsc(MusicPlaylist::getId));
    }

    @Override
    public void add(String name, String playlistId) {
        if (playlistId == null || !playlistId.trim().matches("\\d+")) {
            throw new BusinessException("歌单 ID 必须是纯数字");
        }
        Long count = playlistMapper.selectCount(null);
        MusicPlaylist p = new MusicPlaylist();
        p.setName(name == null || name.trim().isEmpty() ? "歌单 " + playlistId.trim() : name.trim());
        p.setPlaylistId(playlistId.trim());
        p.setActive(count == 0 ? 1 : 0);
        playlistMapper.insert(p);
    }

    @Override
    public void delete(Long id) {
        playlistMapper.deleteById(id);
        // 删除后若没有启用中的歌单，则自动启用第一个
        if (playlistMapper.selectCount(new LambdaQueryWrapper<MusicPlaylist>()
                .eq(MusicPlaylist::getActive, 1)) == 0) {
            MusicPlaylist first = playlistMapper.selectOne(new LambdaQueryWrapper<MusicPlaylist>()
                    .orderByAsc(MusicPlaylist::getId).last("limit 1"));
            if (first != null) {
                first.setActive(1);
                playlistMapper.updateById(first);
            }
        }
    }

    @Override
    public void setActive(Long id) {
        // 先把所有歌单置为未启用，再把目标歌单置为启用
        playlistMapper.update(null, new LambdaUpdateWrapper<MusicPlaylist>()
                .set(MusicPlaylist::getActive, 0));
        MusicPlaylist p = new MusicPlaylist();
        p.setId(id);
        p.setActive(1);
        playlistMapper.updateById(p);
    }

    @Override
    public String activePlaylistId() {
        MusicPlaylist p = playlistMapper.selectOne(new LambdaQueryWrapper<MusicPlaylist>()
                .eq(MusicPlaylist::getActive, 1).last("limit 1"));
        return p == null ? null : p.getPlaylistId();
    }
}
