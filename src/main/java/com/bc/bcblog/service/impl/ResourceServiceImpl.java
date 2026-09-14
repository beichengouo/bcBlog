package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.BlogResource;
import com.bc.bcblog.entity.SysResourceUnlock;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.BlogResourceMapper;
import com.bc.bcblog.mapper.SysResourceUnlockMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.PointService;
import com.bc.bcblog.service.ResourceService;
import com.bc.bcblog.vo.ResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 智库资源服务实现。 */
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final BlogResourceMapper resourceMapper;
    private final SysResourceUnlockMapper unlockMapper;
    private final SysUserMapper userMapper;
    private final PointService pointService;

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
        if (resource.getPoints() == null || resource.getPoints() < 0) {
            resource.setPoints(1);
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

    @Override
    public List<ResourceVO> portalList(Long userId) {
        boolean admin = isAdmin(userId);
        return list().stream()
                .map(r -> toVO(r, admin || isUnlocked(userId, r.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public ResourceVO detail(Long id, Long userId) {
        BlogResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        return toVO(resource, isAdmin(userId) || isUnlocked(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceVO unlock(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再前往资源");
        }
        BlogResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException("资源不存在");
        }
        // 管理员不需要积分，直接解锁
        if (isAdmin(userId)) {
            return toVO(resource, true);
        }
        if (isUnlocked(userId, id)) {
            return toVO(resource, true);
        }
        int points = resource.getPoints() == null ? 1 : resource.getPoints();
        if (points > 0) {
            pointService.deductPoints(userId, points, "resource", "解锁资源：" + resource.getTitle());
        }
        SysResourceUnlock unlock = new SysResourceUnlock();
        unlock.setUserId(userId);
        unlock.setResourceId(id);
        unlock.setCreateTime(LocalDateTime.now());
        unlockMapper.insert(unlock);
        return toVO(resource, true);
    }

    private boolean isUnlocked(Long userId, Long resourceId) {
        if (userId == null) {
            return false;
        }
        return unlockMapper.selectCount(new LambdaQueryWrapper<SysResourceUnlock>()
                .eq(SysResourceUnlock::getUserId, userId)
                .eq(SysResourceUnlock::getResourceId, resourceId)) > 0;
    }

    /** 管理员账号下载资源不需要积分 */
    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        return "SUPER".equals(user.getRole()) || "ADMIN1".equals(user.getRole()) || "ADMIN2".equals(user.getRole());
    }

    private ResourceVO toVO(BlogResource resource, boolean unlocked) {
        ResourceVO vo = new ResourceVO();
        vo.setId(resource.getId());
        vo.setTitle(resource.getTitle());
        vo.setDescription(resource.getDescription());
        vo.setCover(resource.getCover());
        vo.setPoints(resource.getPoints() == null ? 1 : resource.getPoints());
        vo.setContent(resource.getContent());
        vo.setUnlocked(unlocked);
        vo.setCreateTime(resource.getCreateTime());
        if (unlocked) {
            vo.setUrl(resource.getUrl());
            vo.setPassword(resource.getPassword());
        }
        return vo;
    }
}
