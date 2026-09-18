package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.dto.ChatMessage;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.AiProviderMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.service.AuditLogService;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.vo.AiArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** AI 服务商服务，兼容 OpenAI 风格接口。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProviderServiceImpl implements AiProviderService {

    private final AiProviderMapper providerMapper;
    private final SysUserMapper sysUserMapper;
    private final SecretCipher secretCipher;
    private final AuditLogService auditLogService;

    @Override
    public List<AiProvider> list() {
        Long uid = currentAdminId();
        boolean superAdmin = isSuper(uid);
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<AiProvider>()
                .orderByDesc(AiProvider::getIsDefault)
                .orderByAsc(AiProvider::getId);
        if (superAdmin || uid == null) {
            // 超管：系统服务商 + 自己的
            wrapper.and(w -> w.isNull(AiProvider::getOwnerId).or().eq(AiProvider::getOwnerId, uid));
        } else {
            // 其他管理员：只看到自己的
            wrapper.eq(AiProvider::getOwnerId, uid);
        }
        List<AiProvider> list = providerMapper.selectList(wrapper);
        // 回显掩码，绝不返回明文密钥
        list.forEach(p -> p.setApiKey(secretCipher.mask(p.getApiKey())));
        return list;
    }

    @Override
    public void save(AiProvider provider) {
        if (provider.getName() == null || provider.getName().trim().isEmpty()) {
            throw new BusinessException("服务商名称不能为空");
        }
        if (provider.getBaseUrl() == null || provider.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException("接口地址不能为空");
        }
        Long uid = currentAdminId();
        if (uid == null) {
            throw new BusinessException(403, "未登录，无法保存服务商");
        }
        boolean superAdmin = isSuper(uid);
        // 归属：超管维护系统服务商（owner 为空），其他管理员只能用自己的
        provider.setOwnerId(superAdmin ? null : uid);
        // 密钥：提交掩码或空值表示保持原值；存入数据库前加密
        String submittedKey = provider.getApiKey();
        AiProvider existing = provider.getId() == null ? null : providerMapper.selectById(provider.getId());
        if (existing != null) {
            if (!visibleTo(existing, uid, superAdmin)) {
                throw new BusinessException(403, "只能修改自己的服务商");
            }
            if (secretCipher.isUnchanged(submittedKey)) {
                provider.setApiKey(existing.getApiKey());
            } else {
                provider.setApiKey(secretCipher.encrypt(submittedKey.trim()));
            }
        } else if (secretCipher.isUnchanged(submittedKey)) {
            provider.setApiKey(null);
        } else {
            provider.setApiKey(secretCipher.encrypt(submittedKey.trim()));
        }
        if (provider.getId() == null) {
            Long own = providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                    .eq(superAdmin, AiProvider::getOwnerId, uid)
                    .isNull(!superAdmin, AiProvider::getOwnerId));
            provider.setIsDefault(own == null || own == 0 ? 1 : 0);
            providerMapper.insert(provider);
        } else {
            providerMapper.updateById(provider);
        }
    }

    @Override
    public void delete(Long id) {
        AiProvider target = providerMapper.selectById(id);
        if (target == null) {
            return;
        }
        if (!visibleTo(target, currentAdminId(), isSuper(currentAdminId()))) {
            throw new BusinessException(403, "只能删除自己的服务商");
        }
        providerMapper.deleteById(id);
        if (providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getIsDefault, 1)) == 0) {
            AiProvider first = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                    .eq(target.getOwnerId() != null, AiProvider::getOwnerId, target.getOwnerId())
                    .isNull(target.getOwnerId() == null, AiProvider::getOwnerId)
                    .orderByAsc(AiProvider::getId).last("limit 1"));
            if (first != null) {
                first.setIsDefault(1);
                providerMapper.updateById(first);
            }
        }
    }

    @Override
    public void setDefault(Long id) {
        AiProvider target = requireProvider(id);
        Long uid = currentAdminId();
        if (!visibleTo(target, uid, isSuper(uid))) {
            throw new BusinessException(403, "只能操作自己的服务商");
        }
        LambdaUpdateWrapper<AiProvider> wrapper = new LambdaUpdateWrapper<AiProvider>()
                .set(AiProvider::getIsDefault, 0);
        if (target.getOwnerId() == null) {
            wrapper.isNull(AiProvider::getOwnerId);
        } else {
            wrapper.eq(AiProvider::getOwnerId, target.getOwnerId());
        }
        providerMapper.update(null, wrapper);
        AiProvider p = new AiProvider();
        p.setId(id);
        p.setIsDefault(1);
        providerMapper.updateById(p);
    }

    @Override
    public List<String> listModels(Long id) {
        AiProvider p = requireProvider(id);
        if (!visibleTo(p, currentAdminId(), isSuper(currentAdminId()))) {
            throw new BusinessException(403, "只能查看自己的服务商");
        }
        if (isBlank(p.getApiKey())) {
            throw new BusinessException("请先为该服务商配置 API Key");
        }
        String apiKey = secretCipher.decrypt(p.getApiKey());
        String base = trimSlash(p.getBaseUrl());
        for (String u : new String[]{base + "/models", base + "/v1/models"}) {
            try {
                HttpResponse r = HttpRequest.get(u)
                        .header("Authorization", "Bearer " + (apiKey == null ? "" : apiKey.trim()))
                        .timeout(20000)
                        .execute();
                if (r.getStatus() == 200) {
                    JSONObject json = JSONUtil.parseObj(r.body());
                    JSONArray arr = json.getJSONArray("data");
                    List<String> models = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            String mid = arr.getJSONObject(i).getStr("id");
                            if (mid != null && !mid.trim().isEmpty()) {
                                models.add(mid);
                            }
                        }
                    }
                    return models;
                }
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException("获取模型列表失败，请检查接口地址和 API Key");
    }

    @Override
    public AiArticleVO generate(Long providerId, String model, String requirement) {
        AiProvider p = requireProvider(providerId);
        if (isBlank(p.getApiKey())) {
            throw new BusinessException("请先为该服务商配置 API Key");
        }
        if (isBlank(model)) {
            throw new BusinessException("请选择模型");
        }
        if (isBlank(requirement)) {
            throw new BusinessException("请先输入文章需求");
        }

        String base = trimSlash(p.getBaseUrl());
        String[] urls = {base + "/chat/completions", base + "/v1/chat/completions"};
        String key = secretCipher.decrypt(p.getApiKey());
        key = key == null ? "" : key.trim();

        HttpResponse ok = null;
        for (String u : urls) {
            HttpResponse r = tryPost(u, key, buildBody(model, requirement, true));
            if (r != null && r.getStatus() == 200) {
                ok = r;
                break;
            }
            if (r != null && (r.getStatus() == 401 || r.getStatus() == 403)) {
                throw new BusinessException("API Key 无效或无权限");
            }
        }
        // 部分兼容接口不支持 response_format，去掉后重试
        if (ok == null) {
            for (String u : urls) {
                HttpResponse r = tryPost(u, key, buildBody(model, requirement, false));
                if (r != null && r.getStatus() == 200) {
                    ok = r;
                    break;
                }
                if (r != null && (r.getStatus() == 401 || r.getStatus() == 403)) {
                    throw new BusinessException("API Key 无效或无权限");
                }
            }
        }
        if (ok == null) {
            throw new BusinessException("AI 调用失败，请检查接口地址、模型和 API Key");
        }
        try {
            JSONObject json = JSONUtil.parseObj(ok.body());
            JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            return parseContent(message.getStr("content"));
        } catch (Exception e) {
            throw new BusinessException("AI 返回内容解析失败：" + e.getMessage());
        }
    }

    private JSONObject buildBody(String model, String requirement, boolean withFormat) {
        String systemPrompt = "你是一名专业的中文技术博客作者，擅长写出结构清晰、可读性高的文章。"
                + "请根据用户需求写一篇可以直接发布的博客文章。"
                + "你必须严格只输出一个 JSON 对象，不要输出任何解释、前言、后缀或 Markdown 代码块标记。JSON 结构如下："
                + "{\"title\":\"文章标题\",\"summary\":\"60到120字的文章摘要\","
                + "\"content\":\"文章正文，必须使用 HTML 标签，例如 h2、h3、p、ul、li、blockquote、code、pre，不要使用 Markdown 语法\","
                + "\"tags\":[\"标签1\",\"标签2\",\"标签3\"],\"category\":\"文章所属分类名称\"}";
        JSONObject body = new JSONObject();
        body.set("model", model);
        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.set("role", "system");
        sys.set("content", systemPrompt);
        messages.add(sys);
        JSONObject user = new JSONObject();
        user.set("role", "user");
        user.set("content", "请根据以下需求写一篇文章：\n" + requirement.trim());
        messages.add(user);
        body.set("messages", messages);
        body.set("temperature", 0.8);
        if (withFormat) {
            JSONObject rf = new JSONObject();
            rf.set("type", "json_object");
            body.set("response_format", rf);
        }
        return body;
    }

    @Override
    public AiProvider defaultProvider() {
        // 系统服务商 = 归属为空（超管维护），定时任务使用
        AiProvider p = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .isNull(AiProvider::getOwnerId)
                .eq(AiProvider::getIsDefault, 1)
                .orderByAsc(AiProvider::getId)
                .last("limit 1"));
        if (p != null) {
            return p;
        }
        return providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                .isNull(AiProvider::getOwnerId)
                .orderByAsc(AiProvider::getId)
                .last("limit 1"));
    }

    @Override
    public AiProvider resolveManualProvider(Long preferredId) {
        Long uid = currentAdminId();
        // 没有登录上下文（定时任务、命令行/测试脚本）时视同系统调用：
        // 此时不存在"某个普通管理员"这个主体，直接用系统服务商即可。
        // 线上手动执行（HTTP）一定带登录态，所以"用调用者自己的 key"这条规则不受影响。
        if (uid == null) {
            return resolveSystemProvider(preferredId);
        }
        boolean superAdmin = isSuper(uid);
        AiProvider preferred = preferredId == null ? null : providerMapper.selectById(preferredId);
        if (preferred != null && visibleTo(preferred, uid, superAdmin)) {
            return preferred;
        }
        // 角色绑定的是系统/别人的服务商时，改用当前管理员自己的
        if (uid != null) {
            AiProvider own = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                    .eq(AiProvider::getOwnerId, uid)
                    .orderByDesc(AiProvider::getIsDefault)
                    .orderByAsc(AiProvider::getId)
                    .last("limit 1"));
            if (own != null) {
                return own;
            }
        }
        AiProvider system = defaultProvider();
        if (superAdmin && system != null) {
            return system;
        }
        throw new BusinessException("请先在「接口管理 → AI 服务商」里配置你自己的 API Key 与模型");
    }

    @Override
    public AiProvider resolveSystemProvider(Long preferredId) {
        AiProvider preferred = preferredId == null ? null : providerMapper.selectById(preferredId);
        if (preferred != null && preferred.getOwnerId() == null) {
            return preferred;
        }
        AiProvider system = defaultProvider();
        if (system == null) {
            throw new BusinessException("定时任务需要系统服务商，请让超级管理员在「AI 服务商」里配置一个");
        }
        return system;
    }

    /** 服务商是否对当前调用者可见/可用：系统服务商仅超管可用，其他只能用自己的 */
    private boolean visibleTo(AiProvider provider, Long uid, boolean superAdmin) {
        if (provider == null) {
            return false;
        }
        if (provider.getOwnerId() == null) {
            return superAdmin;
        }
        return uid != null && provider.getOwnerId().equals(uid);
    }

    private Long currentAdminId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSuper(Long uid) {
        if (uid == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectById(uid);
        return user != null && "SUPER".equals(user.getRole());
    }

    @Override
    public String chat(Long providerId, String model, String systemPrompt, String userPrompt, Double temperature) {
        // 手动调用用调用者自己的服务商，定时任务（无登录上下文）用系统服务商
        AiProvider provider = currentAdminId() == null
                ? resolveSystemProvider(providerId)
                : resolveManualProvider(providerId);
        return chat(provider, model, systemPrompt, userPrompt, temperature);
    }

    @Override
    public String chat(AiProvider provider, String model, String systemPrompt, String userPrompt, Double temperature) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        return chatMessages(provider, model, messages, temperature, true);
    }

    @Override
    public String chatMessages(AiProvider provider, String model, List<ChatMessage> messages,
                               Double temperature, boolean jsonMode) {
        AiProvider p = provider == null ? resolveManualProvider(null) : provider;
        if (isBlank(p.getApiKey())) {
            throw new BusinessException("服务商「" + p.getName() + "」还没有配置 API Key");
        }
        if (isBlank(model)) {
            throw new BusinessException("请先选择要使用的模型");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException("提示词不能为空");
        }
        long start = System.currentTimeMillis();
        try {
            String result = doChat(p, model, messages, temperature, jsonMode);
            auditLogService.record(p.getName() + " / " + model, true, null, System.currentTimeMillis() - start,
                    result == null ? 0 : result.length());
            return result;
        } catch (Exception e) {
            auditLogService.record(p.getName() + " / " + model, false, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    /** 真正发起请求（密钥在这里解密使用） */
    private String doChat(AiProvider p, String model, List<ChatMessage> messages,
                          Double temperature, boolean jsonMode) {

        String base = trimSlash(p.getBaseUrl());
        String[] urls = {base + "/chat/completions", base + "/v1/chat/completions"};
        // 密钥是加密存储的，这里必须先解密再使用
        String key = secretCipher.decrypt(p.getApiKey());
        key = key == null ? "" : key.trim();
        JSONObject body = new JSONObject();
        body.set("model", model);
        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : messages) {
            JSONObject node = new JSONObject();
            node.set("role", message.getRole());
            node.set("content", message.getContent() == null ? "" : message.getContent());
            messageArray.add(node);
        }
        body.set("messages", messageArray);
        body.set("temperature", temperature == null ? 0.9 : temperature);

        HttpResponse ok = null;
        // 先尝试要求返回 JSON 对象，部分兼容接口不支持时自动去掉该参数重试
        // jsonMode=false 时（例如"草稿+自审+JSON"的混合输出）不能要求 json_object，否则上游会强制纯 JSON
        boolean[] formatAttempts = jsonMode ? new boolean[]{true, false} : new boolean[]{false};
        for (boolean withFormat : formatAttempts) {
            for (String u : urls) {
                JSONObject b = JSONUtil.parseObj(body.toString());
                if (withFormat) {
                    JSONObject rf = new JSONObject();
                    rf.set("type", "json_object");
                    b.set("response_format", rf);
                }
                HttpResponse r = tryPost(u, key, b);
                if (r != null && r.getStatus() == 200) {
                    ok = r;
                    break;
                }
                if (r != null) {
                    // 记录上游返回的原始状态与内容，便于排查「API Key 无效」到底是哪一步被拒
                    String snippet = r.body() == null ? "" : r.body();
                    if (snippet.length() > 300) {
                        snippet = snippet.substring(0, 300);
                    }
                    log.warn("AI 调用被拒绝：url={} status={} keyHead={} body={}",
                            u, r.getStatus(), key.length() > 8 ? key.substring(0, 8) + "..." : "(空/过短)", snippet);
                }
                if (r != null && (r.getStatus() == 401 || r.getStatus() == 403)) {
                    throw new BusinessException("API Key 无效或无权限");
                }
            }
            if (ok != null) {
                break;
            }
        }
        if (ok == null) {
            throw new BusinessException("AI 调用失败，请检查接口地址、模型和 API Key");
        }
        try {
            JSONObject json = JSONUtil.parseObj(ok.body());
            JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            return message.getStr("content");
        } catch (Exception e) {
            throw new BusinessException("AI 返回内容解析失败：" + e.getMessage());
        }
    }

    private HttpResponse tryPost(String url, String key, JSONObject body) {
        try {
            return HttpRequest.post(url)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(120000)
                    .execute();
        } catch (Exception e) {
            return null;
        }
    }

    private AiArticleVO parseContent(String raw) {
        String c = raw == null ? "" : raw.trim();
        // 兼容被 Markdown 代码块包裹的情况
        if (c.startsWith("```")) {
            int i = c.indexOf('\n');
            if (i >= 0) {
                c = c.substring(i + 1);
            }
            if (c.endsWith("```")) {
                c = c.substring(0, c.length() - 3);
            }
            c = c.trim();
        }
        JSONObject obj = JSONUtil.parseObj(c);
        AiArticleVO vo = new AiArticleVO();
        vo.setTitle(obj.getStr("title"));
        vo.setSummary(obj.getStr("summary"));
        vo.setContent(obj.getStr("content"));
        JSONArray tags = obj.getJSONArray("tags");
        List<String> tagList = new ArrayList<>();
        if (tags != null) {
            for (int i = 0; i < tags.size(); i++) {
                tagList.add(tags.getStr(i));
            }
        }
        vo.setTags(tagList);
        vo.setCategory(obj.getStr("category"));
        return vo;
    }

    private AiProvider requireProvider(Long id) {
        AiProvider p = id == null ? null : providerMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("请先选择 AI 服务商");
        }
        return p;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String trimSlash(String s) {
        return s == null ? "" : s.replaceAll("/+$", "");
    }
}
