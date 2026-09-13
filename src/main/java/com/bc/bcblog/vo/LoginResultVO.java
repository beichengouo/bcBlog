package com.bc.bcblog.vo;

import lombok.Data;

@Data
public class LoginResultVO {
    private String token;
    private UserInfoVO user;
}
