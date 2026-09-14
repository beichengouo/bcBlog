package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.BlogResource;
import com.bc.bcblog.mapper.BlogResourceMapper;
import com.bc.bcblog.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 智库资源服务实现。 */
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final BlogResourceMapper resourceMapper;

    @Override
    public List<BlogResource> list() {
        return resourceMapper.selectList(new LambdaQueryWrapper<BlogResource>()
                .orderByDesc(BlogResource::getCreateTime)
                .orderByDesc(BlogResource::getId));
    }

    @Override
    public void save(BlogResource resource) {
        if (resource.getTitle() == null || resource.getTitle().trim().isEmpty()) {
            throw new BusinessException("资源名称不能为空");
        }
        if (resource.getUrl() == null || resource.getUrl().trim().isEmpty()) {
            throw new BusinessException("资源链接不能为空");
        }
        resource.setTitle(resource.getTitle().trim());
        resource.setUrl(resource.getUrl().trim());
        if (resource.getDescription() != null) {
            resource.setDescription(resource.getDescription().trim());
        }
        if (resource.getPassword() != null) {
            resource.setPassword(resource.getPassword().trim());
        }
        if (resource.getId() == null) {
            resource.setCreateTime(LocalDateTime.now());
            resourceMapper.insert(resource);
        } else {
            resourceMapper.updateById(resource);
        }
    }

    @Override
    public void delete(Long id) {
        resourceMapper.deleteById(id);
    }
}
