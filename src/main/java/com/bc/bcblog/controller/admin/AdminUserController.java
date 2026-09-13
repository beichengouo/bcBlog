package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.AdminUserDTO;
import com.bc.bcblog.service.AdminUserService;
import com.bc.bcblog.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台管理员管理接口（仅超级管理员）。 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/list")
    public Result<List<AdminUserVO>> list() {
        return Result.ok(adminUserService.list());
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody AdminUserDTO dto) {
        adminUserService.register(dto);
        return Result.ok();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody AdminUserDTO dto) {
        adminUserService.update(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return Result.ok();
    }
}
