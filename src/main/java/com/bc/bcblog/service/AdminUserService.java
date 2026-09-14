package com.bc.bcblog.service;

import com.bc.bcblog.dto.AdminUserDTO;
import com.bc.bcblog.vo.AdminUserVO;

import java.util.List;

public interface AdminUserService {
    List<AdminUserVO> list();
    void register(AdminUserDTO dto);
    void update(AdminUserDTO dto);
    void delete(Long id);

    /** 普通用户列表 */
    List<AdminUserVO> memberList();

    /** 修改普通用户（昵称/状态/邀请权限/密码） */
    void updateMember(AdminUserDTO dto);

    /** 删除普通用户 */
    void deleteMember(Long id);
}
