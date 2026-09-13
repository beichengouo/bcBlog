package com.bc.bcblog.service;

import com.bc.bcblog.dto.LoginDTO;
import com.bc.bcblog.vo.CaptchaVO;
import com.bc.bcblog.vo.LoginResultVO;
import com.bc.bcblog.vo.UserInfoVO;

import javax.servlet.http.HttpServletRequest;

public interface AuthService {
    CaptchaVO captcha();
    LoginResultVO login(LoginDTO dto, HttpServletRequest request);
    UserInfoVO info();
    /** 修改当前登录管理员的密码 */
    void changePassword(String oldPassword, String newPassword);
}
