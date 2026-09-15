package com.bc.bcblog.service;

import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.vo.AiArticleVO;

import java.util.List;

public interface AiProviderService {
    List<AiProvider> list();
    void save(AiProvider provider);
    void delete(Long id);
    void setDefault(Long id);
    List<String> listModels(Long id);
    AiArticleVO generate(Long providerId, String model, String requirement);

    /**
     * 通用对话调用（OpenAI 兼容接口），供沙盒等模块复用。
     *
     * @param providerId   服务商 ID
     * @param model        模型名
     * @param systemPrompt 系统提示词（角色卡 + 世界书 + 输出要求）
     * @param userPrompt   用户提示词（当前状态 + 最近行动 + 指令）
     * @param temperature  采样温度，为空时用 0.9
     * @return AI 返回的正文（要求是 JSON 字符串，可能被 Markdown 代码块包裹）
     */
    String chat(Long providerId, String model, String systemPrompt, String userPrompt, Double temperature);

    /** 服务商未显式指定时的默认服务商，没有配置任何服务商时返回 null */
    AiProvider defaultProvider();
}
