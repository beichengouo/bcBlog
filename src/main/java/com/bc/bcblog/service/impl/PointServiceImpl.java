package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysPointLogMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/** 积分服务实现。 */
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final SysUserMapper userMapper;
    private final SysPointLogMapper pointLogMapper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public int randomSignPoints() {
        return 1 + random.nextInt(3);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPoints(Long userId, int points, String type, String reason) {
        SysUser user = requireUser(userId);
        int current = user.getPoints() == null ? 0 : user.getPoints();
        int balance = Math.max(0, current + points);
        return updateAndLog(userId, balance, points, type, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long userId, int points, String type, String reason) {
        if (points <= 0) {
            throw new BusinessException("扣减积分必须大于 0");
        }
        SysUser user = requireUser(userId);
        int current = user.getPoints() == null ? 0 : user.getPoints();
        if (current < points) {
            throw new BusinessException("积分不足，需要 " + points + " 积分");
        }
        return updateAndLog(userId, current - points, -points, type, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grant(List<Long> userIds, int points, String type, String reason) {
        if (points == 0) {
            throw new BusinessException("积分不能为 0");
        }
        List<SysUser> users;
        if (userIds == null || userIds.isEmpty()) {
            users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "USER"));
        } else {
            users = userMapper.selectBatchIds(userIds);
        }
        int count = 0;
        for (SysUser user : users) {
            if (user == null || !"USER".equals(user.getRole())) {
                continue;
            }
            int current = user.getPoints() == null ? 0 : user.getPoints();
            int balance = Math.max(0, current + points);
            updateAndLog(user.getId(), balance, points, type, reason);
            count++;
        }
        return count;
    }

    @Override
    public PageResult<SysPointLog> page(Long userId, long page, long size) {
        Page<SysPointLog> p = new Page<>(page, size);
        IPage<SysPointLog> result = pointLogMapper.selectPage(p, new LambdaQueryWrapper<SysPointLog>()
                .eq(SysPointLog::getUserId, userId)
                .orderByDesc(SysPointLog::getCreateTime)
                .orderByDesc(SysPointLog::getId));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult<SysPointLog> pageAll(Long userId, long page, long size) {
        Page<SysPointLog> p = new Page<>(page, size);
        LambdaQueryWrapper<SysPointLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(SysPointLog::getUserId, userId);
        }
        wrapper.orderByDesc(SysPointLog::getCreateTime).orderByDesc(SysPointLog::getId);
        IPage<SysPointLog> result = pointLogMapper.selectPage(p, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("缺少用户 ID");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private int updateAndLog(Long userId, int balance, int change, String type, String reason) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPoints(balance);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);

        SysPointLog log = new SysPointLog();
        log.setUserId(userId);
        log.setType(type);
        log.setPoints(change);
        log.setBalance(balance);
        log.setReason(reason);
        log.setCreateTime(LocalDateTime.now());
        pointLogMapper.insert(log);
        return balance;
    }
}
