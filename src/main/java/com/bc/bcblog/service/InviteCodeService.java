package com.bc.bcblog.service;

import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysUser;

import java.util.List;

/** 邀请码服务。 */
public interface InviteCodeService {
    List<SysInviteCode> list();

    /** 获取或创建用户的邀请码（只有 canInvite=1 的用户才有）。 */
    SysInviteCode getOrCreate(SysUser user);

    /** 校验并使用邀请码。 */
    void validateAndUse(String code);

    /** 重新生成某个用户的邀请码。 */
    SysInviteCode regenerate(Long creatorId);

    /** 设置用户是否拥有邀请码权限。 */
    void updatePermission(Long userId, boolean canInvite);
}
