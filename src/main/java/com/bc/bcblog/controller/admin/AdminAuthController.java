package com.bc.bcblog.controller.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.ChangePasswordDTO;
import com.bc.bcblog.service.AuthService;
import com.bc.bcblog.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.ok(authService.info());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    /** 修改当前管理员密码 */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.ok();
    }
}
