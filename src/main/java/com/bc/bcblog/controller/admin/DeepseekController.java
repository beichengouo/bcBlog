package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.AiArticleRequestDTO;
import com.bc.bcblog.dto.DeepseekApiKeyDTO;
import com.bc.bcblog.service.DeepseekService;
import com.bc.bcblog.vo.AiArticleVO;
import com.bc.bcblog.vo.DeepseekBalanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bc.bcblog.component.SecretCipher;

/**
 * DeepSeek 余额查询接口。
 */
@RestController
@RequestMapping("/api/admin/deepseek")
@RequiredArgsConstructor
public class DeepseekController {

    private final DeepseekService deepseekService;
    private final SecretCipher secretCipher;

    @GetMapping("/api-key")
    public Result<String> apiKey() {
        // 只回显掩码，明文不出后台接口
        return Result.ok(secretCipher.mask(deepseekService.getApiKey()));
    }

    @PostMapping("/api-key")
    public Result<Void> saveApiKey(@RequestBody DeepseekApiKeyDTO dto) {
        deepseekService.saveApiKey(dto.getApiKey());
        return Result.ok();
    }

    @GetMapping("/balance")
    public Result<DeepseekBalanceVO> balance() {
        return Result.ok(deepseekService.queryBalance());
    }

    /** AI 一键写文章 */
    @PostMapping("/article")
    public Result<AiArticleVO> article(@RequestBody AiArticleRequestDTO dto) {
        return Result.ok(deepseekService.generateArticle(dto.getRequirement()));
    }
}
