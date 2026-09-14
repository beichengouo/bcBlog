package com.bc.bcblog.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.InviteCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台邀请码管理接口。 */
@RestController
@RequestMapping("/api/admin/invite")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteCodeService inviteCodeService;
    private final SysUserMapper sysUserMapper;

    @GetMapping("/list")
    public Result<List<SysInviteCode>> list() {
        return Result.ok(inviteCodeService.list());
    }

    @PutMapping("/{userId}/permission")
    public Result<Void> permission(@PathVariable Long userId, @RequestParam boolean canInvite) {
        inviteCodeService.updatePermission(userId, canInvite);
        return Result.ok();
    }

    @PutMapping("/{userId}/regenerate")
    public Result<SysInviteCode> regenerate(@PathVariable Long userId) {
        return Result.ok(inviteCodeService.regenerate(userId));
    }

    /** 生成/获取当前登录管理员的邀请码 */
    @PostMapping("/mine")
    public Result<SysInviteCode> mine() {
        SysUser user = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return Result.ok(inviteCodeService.getOrCreate(user));
    }
}
