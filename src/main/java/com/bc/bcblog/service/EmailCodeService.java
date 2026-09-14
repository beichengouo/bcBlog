package com.bc.bcblog.service;

import com.bc.bcblog.vo.EmailCodeResultVO;

/** 邮箱验证码服务（当前为内存实现，后续可替换为 QQ 邮箱发送）。 */
public interface EmailCodeService {

    /** 生成并发送验证码；未配置邮件服务时返回验证码用于测试。 */
    EmailCodeResultVO send(String email);

    /** 校验验证码。 */
    void validate(String email, String code);
}
