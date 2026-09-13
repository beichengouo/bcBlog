package com.bc.bcblog.service;

import com.bc.bcblog.entity.MusicPlaylist;

import java.util.List;

public interface MusicPlaylistService {
    List<MusicPlaylist> list();
    void add(String name, String playlistId);
    void delete(Long id);
    void setActive(Long id);
    String activePlaylistId();
}
