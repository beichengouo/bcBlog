package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.MusicFallback;
import com.bc.bcblog.mapper.MusicFallbackMapper;
import com.bc.bcblog.service.MusicFallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 默认歌曲服务实现。 */
@Service
@RequiredArgsConstructor
public class MusicFallbackServiceImpl implements MusicFallbackService {

    private final MusicFallbackMapper fallbackMapper;

    @Override
    public List<MusicFallback> list() {
        return fallbackMapper.selectList(new LambdaQueryWrapper<MusicFallback>()
                .orderByAsc(MusicFallback::getId));
    }

    @Override
    public void save(MusicFallback song) {
        if (song.getTitle() == null || song.getTitle().trim().isEmpty()) {
            throw new BusinessException("歌曲名称不能为空");
        }
        if (song.getUrl() == null || song.getUrl().trim().isEmpty()) {
            throw new BusinessException("歌曲链接不能为空");
        }
        song.setTitle(song.getTitle().trim());
        song.setUrl(song.getUrl().trim());
        if (song.getArtist() != null) {
            song.setArtist(song.getArtist().trim());
        }
        if (song.getPic() != null) {
            song.setPic(song.getPic().trim());
        }
        if (song.getId() == null) {
            song.setCreateTime(LocalDateTime.now());
            fallbackMapper.insert(song);
        } else {
            fallbackMapper.updateById(song);
        }
    }

    @Override
    public void delete(Long id) {
        fallbackMapper.deleteById(id);
    }
}
