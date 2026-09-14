package com.bc.bcblog.vo;

import lombok.Data;

/** 邮箱验证码发送结果。 */
@Data
public class EmailCodeResultVO {
    /** 是否已真实发送到邮箱 */
    private Boolean sent;
    /** 未配置邮件服务时返回验证码用于测试 */
    private String code;
}
