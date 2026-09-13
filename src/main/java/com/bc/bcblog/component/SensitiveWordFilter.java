package com.bc.bcblog.component;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感词过滤组件。
 * 默认内置一批常见词，并支持通过配置 bcblog.sensitive-words 追加（英文逗号分隔）。
 * 命中后替换为 ***，属于简单实现，后续可替换为 Aho-Corasick 等更高效算法。
 */
@Component
public class SensitiveWordFilter {

    /** UAPIS 敏感词快速检测接口。 */
    private static final String UAPIS_PROFANITY_URL = "https://uapis.cn/api/v1/text/profanitycheck";

    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            "傻逼", "妈的", "操你", "操你妈", "fuck", "shit", "白痴", "去死", "垃圾"
    );

    private final List<String> words = new ArrayList<>();

    public SensitiveWordFilter(@Value("${bcblog.sensitive-words:}") String extraWords) {
        words.addAll(DEFAULT_WORDS);
        if (extraWords != null && !extraWords.trim().isEmpty()) {
            for (String w : extraWords.split(",")) {
                if (w != null && !w.trim().isEmpty()) {
                    words.add(w.trim());
                }
            }
        }
    }

    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 优先使用 UAPIS 敏感词检测，命中时返回脱敏后的文本
        String masked = filterByUapis(text);
        if (masked != null) {
            return masked;
        }
        // UAPIS 调用失败时，回退到本地敏感词过滤
        String result = text;
        for (String w : words) {
            result = Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE)
                    .matcher(result).replaceAll("***");
        }
        return result;
    }

    /** 调用 UAPIS 快速敏感词检测，失败时返回 null。 */
    private String filterByUapis(String text) {
        try {
            HttpResponse resp = HttpRequest.post(UAPIS_PROFANITY_URL)
                    .header("Content-Type", "application/json")
                    .timeout(6000)
                    .body(JSONUtil.createObj().set("text", text).toString())
                    .execute();
            if (resp.getStatus() != 200) {
                return null;
            }
            JSONObject body = JSONUtil.parseObj(resp.body());
            if ("forbidden".equals(body.getStr("status"))) {
                String masked = body.getStr("masked_text");
                return masked == null || masked.isEmpty() ? text : masked;
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }
}
