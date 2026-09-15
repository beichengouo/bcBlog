package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.EmailTemplate;
import com.bc.bcblog.service.EmailService;
import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.vo.EmailConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 后台邮件配置与模板管理接口。 */
@RestController
@RequestMapping("/api/admin/email")
@RequiredArgsConstructor
public class AdminEmailController {

    private final EmailService emailService;
    private final SecretCipher secretCipher;

    @GetMapping("/config")
    public Result<EmailConfigVO> config() {
        EmailConfigVO vo = emailService.getConfig();
        // 授权码只回显掩码，避免明文泄露
        if (vo != null) {
            vo.setAuthCode(secretCipher.mask(vo.getAuthCode()));
        }
        return Result.ok(vo);
    }

    @PostMapping("/config")
    public Result<Void> saveConfig(@RequestBody EmailConfigVO vo) {
        emailService.saveConfig(vo);
        return Result.ok();
    }

    @GetMapping("/templates")
    public Result<List<EmailTemplate>> templates() {
        return Result.ok(emailService.templates());
    }

    @PostMapping("/template")
    public Result<Void> saveTemplate(@RequestBody EmailTemplate template) {
        emailService.saveTemplate(template);
        return Result.ok();
    }

    @DeleteMapping("/template/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        emailService.deleteTemplate(id);
        return Result.ok();
    }

    /** 将指定模板设为所属场景的当前启用模板 */
    @PostMapping("/template/{id}/active")
    public Result<Void> activateTemplate(@PathVariable Long id) {
        emailService.activateTemplate(id);
        return Result.ok();
    }

    /** 发送测试邮件 */
    @PostMapping("/test")
    public Result<Void> test(@RequestBody Map<String, String> body) {
        String to = body.get("to");
        String scenario = body.getOrDefault("scenario", "register_code");
        if (to == null || !to.contains("@")) {
            throw new BusinessException("请输入正确的收件邮箱");
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("code", "123456");
        vars.put("email", to);
        vars.put("nickname", "测试用户");
        emailService.sendTemplate(scenario, to.trim(), vars);
        return Result.ok();
    }
}
