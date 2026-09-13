package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.AiGenerateRequestDTO;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.vo.AiArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台 AI 服务商管理接口。 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiProviderController {

    private final AiProviderService aiProviderService;

    @GetMapping("/provider/list")
    public Result<List<AiProvider>> list() {
        return Result.ok(aiProviderService.list());
    }

    @PostMapping("/provider/save")
    public Result<Void> save(@RequestBody AiProvider provider) {
        aiProviderService.save(provider);
        return Result.ok();
    }

    @DeleteMapping("/provider/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiProviderService.delete(id);
        return Result.ok();
    }

    @PutMapping("/provider/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        aiProviderService.setDefault(id);
        return Result.ok();
    }

    @GetMapping("/provider/{id}/models")
    public Result<List<String>> models(@PathVariable Long id) {
        return Result.ok(aiProviderService.listModels(id));
    }

    @PostMapping("/article")
    public Result<AiArticleVO> article(@RequestBody AiGenerateRequestDTO dto) {
        return Result.ok(aiProviderService.generate(dto.getProviderId(), dto.getModel(), dto.getRequirement()));
    }
}
