package com.bc.bcblog.dto;

import lombok.Data;

/** 前台用户登录入参。 */
@Data
public class UserLoginDTO {
    /** 用户名或邮箱 */
    private String account;
    private String password;
}
