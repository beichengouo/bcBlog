package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AdminKeyService;
import com.bc.bcblog.service.AuditLogService;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.mapper.SysConfigMapper;
import com.bc.bcblog.service.DeepseekService;
import com.bc.bcblog.vo.AiArticleVO;
import com.bc.bcblog.vo.DeepseekBalanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 余额查询服务，API Key 存储在 sys_config 表中。
 */
@Service
@RequiredArgsConstructor
public class DeepseekServiceImpl implements DeepseekService {

    private static final String KEY = "deepseek_api_key";
    private static final String URL = "https://api.deepseek.com/user/balance";
    private static final String CHAT_URL = "https://api.deepseek.com/chat/completions";

    private final SysConfigMapper configMapper;
    private final ConfigService configService;
    private final AdminKeyService adminKeyService;
    private final SecretCipher secretCipher;
    private final SysUserMapper sysUserMapper;
    private final AuditLogService auditLogService;

    @Override
    public String getApiKey() {
        Long uid = currentAdminId();
        // 超管与定时任务使用系统 Key；其他管理员使用自己配置的 Key
        if (uid == null || isSuper(uid)) {
            return configService.getConfigValue(KEY, "");
        }
        return adminKeyService.get(uid, KEY);
    }

    @Override
    public void saveApiKey(String apiKey) {
        if (secretCipher.isUnchanged(apiKey)) {
            return;
        }
        Long uid = currentAdminId();
        if (uid == null) {
            throw new BusinessException(403, "未登录");
        }
        if (isSuper(uid)) {
            configService.setConfigValue(KEY, apiKey.trim());
        } else {
            adminKeyService.save(uid, KEY, apiKey.trim());
        }
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
    public DeepseekBalanceVO queryBalance() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("请先配置你自己的 DeepSeek API Key");
        }
        long start = System.currentTimeMillis();
        try {
            DeepseekBalanceVO vo = queryBalanceInternal(apiKey);
            auditLogService.record("DeepSeek 余额查询", true, null, System.currentTimeMillis() - start);
            return vo;
        } catch (RuntimeException e) {
            auditLogService.record("DeepSeek 余额查询", false, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private DeepseekBalanceVO queryBalanceInternal(String apiKey) {

        HttpResponse resp;
        try {
            resp = HttpRequest.get(URL)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Accept", "application/json")
                    .timeout(10000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("余额查询失败：" + e.getMessage());
        }

        if (resp.getStatus() == 401 || resp.getStatus() == 403) {
            throw new BusinessException("API Key 无效或无权限");
        }
        if (resp.getStatus() != 200) {
            throw new BusinessException("余额查询失败（HTTP " + resp.getStatus() + "）");
        }
        return parse(resp.body());
    }

    @Override
    public AiArticleVO generateArticle(String requirement) {
        if (requirement == null || requirement.trim().isEmpty()) {
            throw new BusinessException("请先输入文章需求");
        }
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("请先在 DeepSeek 余额页配置 API Key");
        }

        String systemPrompt = "你是一名专业的中文技术博客作者，擅长写出结构清晰、可读性高的文章。"
                + "请根据用户需求写一篇可以直接发布的博客文章。"
                + "你必须严格只输出一个 JSON 对象，不要输出任何解释、前言、后缀或 Markdown 代码块标记。JSON 结构如下："
                + "{\"title\":\"文章标题\",\"summary\":\"60到120字的文章摘要\","
                + "\"content\":\"文章正文，必须使用 HTML 标签，例如 h2、h3、p、ul、li、blockquote、code、pre，不要使用 Markdown 语法\","
                + "\"tags\":[\"标签1\",\"标签2\",\"标签3\"],\"category\":\"文章所属分类名称\"}";

        JSONObject body = new JSONObject();
        body.set("model", "deepseek-chat");
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
        JSONObject responseFormat = new JSONObject();
        responseFormat.set("type", "json_object");
        body.set("response_format", responseFormat);

        HttpResponse resp;
        try {
            resp = HttpRequest.post(CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(120000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("AI 生成失败：" + e.getMessage());
        }
        if (resp.getStatus() == 401 || resp.getStatus() == 403) {
            throw new BusinessException("API Key 无效或无权限");
        }
        if (resp.getStatus() != 200) {
            throw new BusinessException("AI 生成失败（HTTP " + resp.getStatus() + "）");
        }
        try {
            JSONObject json = JSONUtil.parseObj(resp.body());
            JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            JSONObject obj = JSONUtil.parseObj(message.getStr("content"));
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
        } catch (Exception e) {
            throw new BusinessException("AI 返回内容解析失败：" + e.getMessage());
        }
    }

    private DeepseekBalanceVO parse(String body) {
        JSONObject json = JSONUtil.parseObj(body);
        DeepseekBalanceVO vo = new DeepseekBalanceVO();
        vo.setAvailable(json.getBool("is_available", false));

        JSONArray arr = json.getJSONArray("balance_infos");
        List<DeepseekBalanceVO.BalanceInfo> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DeepseekBalanceVO.BalanceInfo info = new DeepseekBalanceVO.BalanceInfo();
                info.setCurrency(o.getStr("currency"));
                info.setTotalBalance(o.getStr("total_balance"));
                info.setGrantedBalance(o.getStr("granted_balance"));
                info.setToppedUpBalance(o.getStr("topped_up_balance"));
                list.add(info);
            }
        }
        vo.setBalanceInfos(list);
        return vo;
    }
}
