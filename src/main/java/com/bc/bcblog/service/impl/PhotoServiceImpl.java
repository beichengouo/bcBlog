package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.BlogPhoto;
import com.bc.bcblog.mapper.BlogPhotoMapper;
import com.bc.bcblog.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 流光忆庭照片服务实现。 */
@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private final BlogPhotoMapper photoMapper;

    @Override
    public List<BlogPhoto> list() {
        return photoMapper.selectList(new LambdaQueryWrapper<BlogPhoto>()
                .orderByDesc(BlogPhoto::getCreateTime)
                .orderByDesc(BlogPhoto::getId));
    }

    @Override
    public void save(BlogPhoto photo) {
        if (photo.getUrl() == null || photo.getUrl().trim().isEmpty()) {
            throw new BusinessException("请先上传照片");
        }
        photo.setUrl(photo.getUrl().trim());
        if (photo.getTitle() != null) {
            photo.setTitle(photo.getTitle().trim());
        }
        if (photo.getDescription() != null) {
            photo.setDescription(photo.getDescription().trim());
        }
        if (photo.getId() == null) {
            photo.setCreateTime(LocalDateTime.now());
            photoMapper.insert(photo);
        } else {
            photoMapper.updateById(photo);
        }
    }

    @Override
    public void delete(Long id) {
        photoMapper.deleteById(id);
    }
}
