package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysInviteCodeMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.InviteCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 邀请码服务实现。邀请码可无限次使用，短时间频繁使用会自动更换。 */
@Service
@RequiredArgsConstructor
public class InviteCodeServiceImpl implements InviteCodeService {

    /** 同一邀请码连续使用达到该次数且发生在 10 分钟内时自动更换 */
    private static final int FREQUENT_LIMIT = 5;
    private static final long FREQUENT_WINDOW_MINUTES = 10;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SysInviteCodeMapper inviteCodeMapper;
    private final SysUserMapper userMapper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public List<SysInviteCode> list() {
        return inviteCodeMapper.selectList(new LambdaQueryWrapper<SysInviteCode>()
                .orderByDesc(SysInviteCode::getCreateTime));
    }

    @Override
    public SysInviteCode getOrCreate(SysUser user) {
        if (user == null || user.getCanInvite() == null || user.getCanInvite() != 1) {
            return null;
        }
        SysInviteCode existing = inviteCodeMapper.selectOne(new LambdaQueryWrapper<SysInviteCode>()
                .eq(SysInviteCode::getCreatorId, user.getId()));
        if (existing != null) {
            return existing;
        }
        SysInviteCode code = new SysInviteCode();
        code.setCode(randomCode());
        code.setCreatorId(user.getId());
        code.setCreatorName(user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername() : user.getNickname());
        code.setUseCount(0);
        code.setCreateTime(LocalDateTime.now());
        inviteCodeMapper.insert(code);
        return code;
    }

    @Override
    public void validateAndUse(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("请输入邀请码");
        }
        SysInviteCode invite = inviteCodeMapper.selectOne(new LambdaQueryWrapper<SysInviteCode>()
                .eq(SysInviteCode::getCode, code.trim().toUpperCase()));
        if (invite == null) {
            throw new BusinessException("邀请码无效");
        }
        LocalDateTime now = LocalDateTime.now();
        int useCount = (invite.getUseCount() == null ? 0 : invite.getUseCount()) + 1;
        boolean frequent = useCount >= FREQUENT_LIMIT
                && invite.getLastUsedAt() != null
                && Duration.between(invite.getLastUsedAt(), now).toMinutes() < FREQUENT_WINDOW_MINUTES;

        SysInviteCode update = new SysInviteCode();
        update.setId(invite.getId());
        if (frequent) {
            // 频繁使用，自动更换新邀请码
            update.setCode(randomCode());
            update.setUseCount(0);
            update.setLastUsedAt(now);
        } else {
            update.setUseCount(useCount);
            update.setLastUsedAt(now);
        }
        inviteCodeMapper.updateById(update);
    }

    @Override
    public SysInviteCode regenerate(Long creatorId) {
        SysInviteCode invite = inviteCodeMapper.selectOne(new LambdaQueryWrapper<SysInviteCode>()
                .eq(SysInviteCode::getCreatorId, creatorId));
        if (invite == null) {
            SysUser user = userMapper.selectById(creatorId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            user.setCanInvite(1);
            return getOrCreate(user);
        }
        invite.setCode(randomCode());
        invite.setUseCount(0);
        invite.setLastUsedAt(null);
        inviteCodeMapper.updateById(invite);
        return invite;
    }

    @Override
    public void updatePermission(Long userId, boolean canInvite) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setCanInvite(canInvite ? 1 : 0);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        if (canInvite) {
            user.setCanInvite(1);
            getOrCreate(user);
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
