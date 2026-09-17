package com.bc.bcblog.service;

import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.dto.ChatMessage;
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

    /** 手动调用（管理员点击触发）：解析应当使用的服务商，没有可用服务商时抛业务异常 */
    AiProvider resolveManualProvider(Long preferredId);

    /** 定时任务调用：只允许使用系统服务商（归属为空），否则回落到系统默认 */
    AiProvider resolveSystemProvider(Long preferredId);

    /** 直接用一个已解析好的服务商发起对话（内部会解密密钥并写审计日志） */
    String chat(AiProvider provider, String model, String systemPrompt, String userPrompt, Double temperature);

    /**
     * 按消息数组调用（支持 assistant 预填充）。
     *
     * @param messages 按顺序排列的消息；最后一条可以是 {@code assistant}，模型会"接着写"，
     *                 用来把输出强制引到我们期望的格式上（例如以 &lt;draft&gt; 开头）
     * @param jsonMode 是否要求返回 JSON 对象（response_format=json_object）。
     *                 注意：做"草稿+JSON"这种混合输出时必须传 false，否则上游会强制纯 JSON
     */
    String chatMessages(AiProvider provider, String model, List<ChatMessage> messages,
                        Double temperature, boolean jsonMode);
}
