package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysLevel;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysLevelMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.LevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 等级服务实现。 */
@Service
@RequiredArgsConstructor
public class LevelServiceImpl implements LevelService {

    private final SysLevelMapper levelMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<SysLevel> list() {
        return levelMapper.selectList(new LambdaQueryWrapper<SysLevel>()
                .orderByAsc(SysLevel::getLevel));
    }

    @Override
    public SysLevel levelOf(int exp) {
        List<SysLevel> levels = list();
        SysLevel match = null;
        for (SysLevel level : levels) {
            if (level.getExpRequired() != null && exp >= level.getExpRequired()) {
                match = level;
            }
        }
        if (match == null) {
            match = new SysLevel();
            match.setLevel(1);
            match.setName("初来乍到");
            match.setExpRequired(0);
        }
        return match;
    }

    @Override
    public SysLevel nextOf(int exp) {
        for (SysLevel level : list()) {
            if (level.getExpRequired() != null && level.getExpRequired() > exp) {
                return level;
            }
        }
        return null;
    }

    @Override
    public SysLevel addExp(SysUser user, int exp) {
        if (user == null) {
            return null;
        }
        int current = user.getExp() == null ? 0 : user.getExp();
        int updated = Math.max(0, current + exp);
        SysLevel level = levelOf(updated);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setExp(updated);
        update.setLevel(level.getLevel());
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        user.setExp(updated);
        user.setLevel(level.getLevel());
        return level;
    }

    @Override
    public void save(SysLevel level) {
        if (level.getLevel() == null || level.getLevel() < 1) {
            throw new BusinessException("等级必须大于 0");
        }
        if (level.getName() == null || level.getName().trim().isEmpty()) {
            throw new BusinessException("等级名称不能为空");
        }
        if (level.getExpRequired() == null || level.getExpRequired() < 0) {
            throw new BusinessException("所需经验不能小于 0");
        }
        level.setName(level.getName().trim());
        if (level.getId() == null) {
            level.setCreateTime(LocalDateTime.now());
            levelMapper.insert(level);
        } else {
            levelMapper.updateById(level);
        }
    }

    @Override
    public void delete(Long id) {
        levelMapper.deleteById(id);
    }
}
