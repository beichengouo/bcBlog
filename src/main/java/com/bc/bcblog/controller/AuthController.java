package com.bc.bcblog.controller;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.LoginDTO;
import com.bc.bcblog.service.AuthService;
import com.bc.bcblog.vo.CaptchaVO;
import com.bc.bcblog.vo.LoginResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.captcha());
    }

    @PostMapping("/login")
    public Result<LoginResultVO> login(@Validated @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, request));
    }
}
