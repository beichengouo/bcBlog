package com.bc.bcblog.dto;

import lombok.Data;

import java.util.List;

/** 管理员注册/编辑入参。 */
@Data
public class AdminUserDTO {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String role;
    private List<String> menus;
    private Integer status;
}
