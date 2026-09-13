package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.BlogTag;
import com.bc.bcblog.mapper.BlogTagMapper;
import com.bc.bcblog.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签管理实现：标签名唯一，新增/修改时做重名校验。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final BlogTagMapper tagMapper;

    @Override
    public List<BlogTag> list() {
        return tagMapper.selectList(new LambdaQueryWrapper<BlogTag>()
                .orderByDesc(BlogTag::getCreateTime));
    }

    @Override
    public void save(BlogTag tag) {
        checkName(tag, null);
        tag.setId(null);
        tag.setCreateTime(LocalDateTime.now());
        tagMapper.insert(tag);
    }

    @Override
    public void update(BlogTag tag) {
        if (tag.getId() == null) {
            throw new BusinessException("缺少标签ID");
        }
        checkName(tag, tag.getId());
        tagMapper.updateById(tag);
    }

    @Override
    public void delete(Long id) {
        tagMapper.deleteById(id);
    }

    /** 校验标签名非空且不重复 */
    private void checkName(BlogTag tag, Long excludeId) {
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            throw new BusinessException("标签名不能为空");
        }
        tag.setName(tag.getName().trim());
        LambdaQueryWrapper<BlogTag> wrapper = new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getName, tag.getName());
        if (excludeId != null) {
            wrapper.ne(BlogTag::getId, excludeId);
        }
        Long count = tagMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("标签名已存在");
        }
    }
}
