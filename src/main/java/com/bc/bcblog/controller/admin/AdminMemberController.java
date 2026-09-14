package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.AdminUserDTO;
import com.bc.bcblog.service.AdminUserService;
import com.bc.bcblog.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台普通用户管理（仅超级管理员）。 */
@RestController
@RequestMapping("/api/admin/member")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminUserService adminUserService;

    @GetMapping("/list")
    public Result<List<AdminUserVO>> list() {
        return Result.ok(adminUserService.memberList());
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody AdminUserDTO dto) {
        adminUserService.updateMember(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.deleteMember(id);
        return Result.ok();
    }
}
