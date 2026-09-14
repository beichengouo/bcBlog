package com.bc.bcblog.service;

import com.bc.bcblog.entity.MusicFallback;

import java.util.List;

/** 默认歌曲服务。 */
public interface MusicFallbackService {
    List<MusicFallback> list();
    void save(MusicFallback song);
    void delete(Long id);
}
