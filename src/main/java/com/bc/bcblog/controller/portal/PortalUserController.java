package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.UserLoginDTO;
import com.bc.bcblog.dto.UserRegisterDTO;
import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.service.UploadService;
import com.bc.bcblog.service.UserService;
import com.bc.bcblog.vo.CommentVO;
import com.bc.bcblog.vo.EmailCodeResultVO;
import com.bc.bcblog.vo.LoginResultVO;
import com.bc.bcblog.vo.SignResultVO;
import com.bc.bcblog.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 前台用户接口。 */
@RestController
@RequestMapping("/api/portal/user")
@RequiredArgsConstructor
public class PortalUserController {

    private final UserService userService;
    private final UploadService uploadService;

    /** 发送邮箱验证码（当前阶段直接返回验证码用于测试） */
    @PostMapping("/email-code")
    public Result<EmailCodeResultVO> emailCode(@RequestBody Map<String, String> body) {
        return Result.ok(userService.sendEmailCode(body.get("email")));
    }

    @PostMapping("/register")
    public Result<LoginResultVO> register(@RequestBody UserRegisterDTO dto) {
        return Result.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginResultVO> login(@RequestBody UserLoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.ok(userService.info());
    }

    @PostMapping("/sign-in")
    public Result<SignResultVO> signIn() {
        return Result.ok(userService.signIn());
    }

    @PostMapping("/avatar")
    public Result<UserInfoVO> avatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.updateAvatar(uploadService.uploadImage(file)));
    }

    @GetMapping("/my-comments")
    public Result<PageResult<CommentVO>> myComments(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.myComments(page, size));
    }

    @GetMapping("/invite-code")
    public Result<SysInviteCode> inviteCode() {
        return Result.ok(userService.myInviteCode());
    }

    /** 我的积分流水 */
    @GetMapping("/points")
    public Result<PageResult<SysPointLog>> points(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.pointLogs(page, size));
    }
}
