package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.EmailTemplate;
import com.bc.bcblog.mapper.EmailTemplateMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.EmailService;
import com.bc.bcblog.vo.EmailConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 邮件服务实现：QQ 邮箱 SMTP + 后台可编辑 HTML 模板。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final ConfigService configService;
    private final EmailTemplateMapper templateMapper;

    @Override
    public EmailConfigVO getConfig() {
        EmailConfigVO vo = new EmailConfigVO();
        vo.setUsername(configService.getConfigValue("email_username", ""));
        vo.setAuthCode(configService.getConfigValue("email_auth_code", ""));
        vo.setSenderName(configService.getConfigValue("email_sender_name", "bcBlog"));
        vo.setHost(configService.getConfigValue("email_host", "smtp.qq.com"));
        vo.setPort(Integer.parseInt(configService.getConfigValue("email_port", "465")));
        vo.setSsl(Integer.parseInt(configService.getConfigValue("email_ssl", "1")));
        return vo;
    }

    @Override
    public void saveConfig(EmailConfigVO vo) {
        if (vo.getUsername() != null) {
            configService.setConfigValue("email_username", vo.getUsername().trim());
        }
        if (vo.getAuthCode() != null) {
            configService.setConfigValue("email_auth_code", vo.getAuthCode().trim());
        }
        if (vo.getSenderName() != null) {
            configService.setConfigValue("email_sender_name", vo.getSenderName().trim());
        }
        if (vo.getHost() != null) {
            configService.setConfigValue("email_host", vo.getHost().trim());
        }
        if (vo.getPort() != null) {
            configService.setConfigValue("email_port", String.valueOf(vo.getPort()));
        }
        if (vo.getSsl() != null) {
            configService.setConfigValue("email_ssl", vo.getSsl() == 1 ? "1" : "0");
        }
    }

    @Override
    public List<EmailTemplate> templates() {
        return templateMapper.selectList(new LambdaQueryWrapper<EmailTemplate>()
                .orderByAsc(EmailTemplate::getScenario)
                .orderByDesc(EmailTemplate::getActive)
                .orderByAsc(EmailTemplate::getId));
    }

    @Override
    public void saveTemplate(EmailTemplate template) {
        if (template.getScenario() == null || template.getScenario().trim().isEmpty()) {
            throw new BusinessException("场景编码不能为空");
        }
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }
        if (template.getSubject() == null || template.getSubject().trim().isEmpty()) {
            throw new BusinessException("邮件主题不能为空");
        }
        template.setScenario(template.getScenario().trim());
        template.setName(template.getName().trim());
        template.setSubject(template.getSubject().trim());
        if (template.getOverlayOpacity() == null) {
            template.setOverlayOpacity(new BigDecimal("0.9"));
        }
        if (template.getEnabled() == null) {
            template.setEnabled(1);
        }
        // 同一场景只允许一个启用模板；如果该场景还没有启用模板，则新模板默认启用
        Long activeCount = templateMapper.selectCount(new LambdaQueryWrapper<EmailTemplate>()
                .eq(EmailTemplate::getScenario, template.getScenario())
                .eq(EmailTemplate::getActive, 1));
        boolean activate = (template.getActive() != null && template.getActive() == 1)
                || activeCount == null || activeCount == 0;
        if (activate) {
            LambdaUpdateWrapper<EmailTemplate> deactivate = new LambdaUpdateWrapper<>();
            deactivate.eq(EmailTemplate::getScenario, template.getScenario())
                    .set(EmailTemplate::getActive, 0);
            if (template.getId() != null) {
                deactivate.ne(EmailTemplate::getId, template.getId());
            }
            templateMapper.update(null, deactivate);
            template.setActive(1);
        } else {
            template.setActive(0);
        }
        if (template.getId() == null) {
            template.setCreateTime(LocalDateTime.now());
            template.setUpdateTime(LocalDateTime.now());
            templateMapper.insert(template);
        } else {
            template.setUpdateTime(LocalDateTime.now());
            templateMapper.updateById(template);
        }
    }

    @Override
    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
    }

    @Override
    public void activateTemplate(Long id) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        templateMapper.update(null, new LambdaUpdateWrapper<EmailTemplate>()
                .eq(EmailTemplate::getScenario, template.getScenario())
                .set(EmailTemplate::getActive, 0));
        EmailTemplate update = new EmailTemplate();
        update.setId(id);
        update.setActive(1);
        templateMapper.updateById(update);
    }

    @Override
    public boolean isConfigured() {
        EmailConfigVO config = getConfig();
        return config.getUsername() != null && !config.getUsername().trim().isEmpty()
                && config.getAuthCode() != null && !config.getAuthCode().trim().isEmpty();
    }

    @Override
    public void sendTemplate(String scenario, String to, Map<String, Object> variables) {
        if (!isConfigured()) {
            throw new BusinessException("请先配置 QQ 邮箱账号和授权码");
        }
        EmailTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<EmailTemplate>()
                .eq(EmailTemplate::getScenario, scenario)
                .eq(EmailTemplate::getEnabled, 1)
                .orderByDesc(EmailTemplate::getActive)
                .orderByAsc(EmailTemplate::getId)
                .last("limit 1"));
        if (template == null) {
            throw new BusinessException("邮件模板不存在或未启用：" + scenario);
        }
        String siteName = configService.getConfigValue("site_name", "bcBlog");
        String subject = render(template.getSubject(), siteName, variables);
        String content = render(template.getContentHtml(), siteName, variables);
        String html = wrap(template, content);
        sendHtml(to, subject, html);
    }

    /** 使用 {{变量}} 渲染模板 */
    private String render(String text, String siteName, Map<String, Object> variables) {
        String result = text == null ? "" : text;
        result = result.replace("{{siteName}}", siteName == null ? "bcBlog" : siteName);
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                result = result.replace("{{" + entry.getKey() + "}}", value);
            }
        }
        return result;
    }

    /** 用背景图和透明度包裹正文 */
    private String wrap(EmailTemplate template, String content) {
        String html = content == null ? "" : content;
        String lower = html.toLowerCase();
        // 已经是完整 HTML 文档的模板直接使用，不再额外套背景层
        if (lower.contains("<!doctype") || lower.contains("<html")) {
            return html;
        }
        double opacity = template.getOverlayOpacity() == null ? 0.9 : template.getOverlayOpacity().doubleValue();
        opacity = Math.max(0.1, Math.min(1.0, opacity));
        StringBuilder style = new StringBuilder();
        style.append("background-color:#f5f6fa;padding:30px 0;");
        if (template.getBackgroundImage() != null && !template.getBackgroundImage().trim().isEmpty()) {
            style.append("background-image:url('").append(template.getBackgroundImage().trim()).append("');");
            style.append("background-size:cover;background-position:center;");
        }
        String card = "max-width:600px;margin:0 auto;background:rgba(255,255,255," + opacity
                + ");border-radius:16px;padding:32px;";
        return "<div style=\"" + style + "\">"
                + "<div style=\"" + card + "\">" + html + "</div>"
                + "</div>";
    }

    private void sendHtml(String to, String subject, String html) {
        EmailConfigVO config = getConfig();
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(config.getHost());
            sender.setPort(config.getPort() == null ? 465 : config.getPort());
            sender.setUsername(config.getUsername());
            sender.setPassword(config.getAuthCode());
            sender.setDefaultEncoding("UTF-8");

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.timeout", "15000");
            props.put("mail.smtp.connectiontimeout", "15000");
            if (config.getSsl() != null && config.getSsl() == 1) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
            }

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(config.getUsername(), config.getSenderName(), "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败：" + e.getMessage());
        }
    }
}
