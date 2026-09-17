package com.bc.bcblog.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 沙盒物品名规范化。
 *
 * 背景：模型偶尔会中英混排——实测它把「晨雾森林的野浆果」写成了「晨雾森林 of 野浆果」，
 * 因为是原样入库，结果背包里同一个东西因为名字不同被当成两种物品（各占一格）。
 *
 * 处理原则（只做"明显的错"、绝不乱改）：
 *   1. 只替换**孤立的英文连接词**：of → 的、and → 和、the → 去掉；
 *   2. 名字里完全没有英文字母时原样返回（中文名不会被动到）；
 *   3. 剩下的英文单词一律保留（例如 `HK416`、`MP3` 这类真名字）；
 *   4. 收尾清理：合并多余空格、去掉中文字之间的空格。
 */
public final class SandboxItemName {

    /**
     * 孤立的英文连接词 → 中文。
     *
     * 用「前后都不是英文字母」判定，而不是 {@code \b}：
     * Java 的 \b 对中文的边界判定与直觉不一致（实测「晨雾森林of野浆果」里的 of 不会被 \b 命中），
     * 用负向环视既能把中英混排的 of 抓出来，又不会切到 soft / HK416 这类真名字里。
     */
    private static final Pattern[] CONNECTORS = {
            Pattern.compile("(?i)(?<![A-Za-z])of(?![A-Za-z])"),
            Pattern.compile("(?i)(?<![A-Za-z])and(?![A-Za-z])"),
            Pattern.compile("(?i)(?<![A-Za-z])the(?![A-Za-z])")
    };
    private static final String[] REPLACEMENTS = {"的", "和", ""};

    /** 中文字之间的空格（"晨雾森林 的 野浆果" → "晨雾森林的野浆果"） */
    private static final Pattern CJK_SPACES = Pattern.compile("(?<=[\\u4e00-\\u9fa5])\\s+(?=[\\u4e00-\\u9fa5])");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern HAS_LATIN = Pattern.compile("[A-Za-z]");

    private SandboxItemName() {
    }

    /** 规范化物品名；null/空白返回空串 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim().replace('\u3000', ' ');
        if (name.isEmpty()) {
            return name;
        }
        // 只有出现英文字母时才做连接词替换（纯中文名不会被动到）
        if (HAS_LATIN.matcher(name).find()) {
            for (int i = 0; i < CONNECTORS.length; i++) {
                Matcher matcher = CONNECTORS[i].matcher(name);
                name = matcher.replaceAll(REPLACEMENTS[i]);
            }
        }
        // 收尾清理：合并多余空格，并去掉中文字之间的空格
        name = SPACES.matcher(name).replaceAll(" ").trim();
        name = CJK_SPACES.matcher(name).replaceAll("");
        return name.trim();
    }
}
