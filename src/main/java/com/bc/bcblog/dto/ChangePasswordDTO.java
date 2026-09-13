package com.bc.bcblog.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 管理员修改密码入参。
 */
@Data
public class ChangePasswordDTO {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
