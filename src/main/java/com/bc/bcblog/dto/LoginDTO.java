package com.bc.bcblog.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
