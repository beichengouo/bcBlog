package com.bc.bcblog.component;

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
        String result = text;
        for (String w : words) {
            result = Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE)
                    .matcher(result).replaceAll("***");
        }
        return result;
    }
}
