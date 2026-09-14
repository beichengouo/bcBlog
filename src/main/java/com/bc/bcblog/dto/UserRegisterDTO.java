package com.bc.bcblog.dto;

import lombok.Data;

/** 前台用户注册入参。 */
@Data
public class UserRegisterDTO {
    private String email;
    private String emailCode;
    private String password;
    private String confirmPassword;
    private String nickname;
    private String inviteCode;
}
