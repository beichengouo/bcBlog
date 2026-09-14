package com.bc.bcblog.service;

import com.bc.bcblog.entity.EmailTemplate;
import com.bc.bcblog.vo.EmailConfigVO;

import java.util.List;
import java.util.Map;

/** 邮件服务（当前 Provider：QQ 邮箱 SMTP）。 */
public interface EmailService {

    EmailConfigVO getConfig();

    void saveConfig(EmailConfigVO vo);

    List<EmailTemplate> templates();

    void saveTemplate(EmailTemplate template);

    void deleteTemplate(Long id);

    /** 将指定模板设为所属场景的当前启用模板 */
    void activateTemplate(Long id);

    /** 邮件服务是否已配置 */
    boolean isConfigured();

    /** 按场景发送模板邮件 */
    void sendTemplate(String scenario, String to, Map<String, Object> variables);
}
