package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.mapper.AiProviderMapper;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.vo.AiArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** AI 服务商服务，兼容 OpenAI 风格接口。 */
@Service
@RequiredArgsConstructor
public class AiProviderServiceImpl implements AiProviderService {

    private final AiProviderMapper providerMapper;

    @Override
    public List<AiProvider> list() {
        return providerMapper.selectList(new LambdaQueryWrapper<AiProvider>()
                .orderByDesc(AiProvider::getIsDefault)
                .orderByAsc(AiProvider::getId));
    }

    @Override
    public void save(AiProvider provider) {
        if (provider.getName() == null || provider.getName().trim().isEmpty()) {
            throw new BusinessException("服务商名称不能为空");
        }
        if (provider.getBaseUrl() == null || provider.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException("接口地址不能为空");
        }
        if (provider.getId() == null) {
            provider.setIsDefault(providerMapper.selectCount(null) == 0 ? 1 : 0);
            providerMapper.insert(provider);
        } else {
            providerMapper.updateById(provider);
        }
    }

    @Override
    public void delete(Long id) {
        providerMapper.deleteById(id);
        if (providerMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getIsDefault, 1)) == 0) {
            AiProvider first = providerMapper.selectOne(new LambdaQueryWrapper<AiProvider>()
                    .orderByAsc(AiProvider::getId).last("limit 1"));
            if (first != null) {
                first.setIsDefault(1);
                providerMapper.updateById(first);
            }
        }
    }

    @Override
    public void setDefault(Long id) {
        providerMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .set(AiProvider::getIsDefault, 0));
        AiProvider p = new AiProvider();
        p.setId(id);
        p.setIsDefault(1);
        providerMapper.updateById(p);
    }

    @Override
    public List<String> listModels(Long id) {
        AiProvider p = requireProvider(id);
        if (isBlank(p.getApiKey())) {
            throw new BusinessException("请先为该服务商配置 API Key");
        }
        String base = trimSlash(p.getBaseUrl());
        for (String u : new String[]{base + "/models", base + "/v1/models"}) {
            try {
                HttpResponse r = HttpRequest.get(u)
                        .header("Authorization", "Bearer " + p.getApiKey().trim())
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
        String key = p.getApiKey().trim();

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
