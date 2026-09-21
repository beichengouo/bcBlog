package com.bc.bcblog.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 「当前正在处理哪个世界的沙盒参数」的线程作用域（方案 C：每个世界的参数完全独立）。
 *
 * 为什么用 ThreadLocal 而不是给每个读取点加 worldId 参数：
 * 沙盒参数（{@code sandbox_*}）在服务里被读了两百来处，绝大多数方法本身拿得到角色或世界，
 * 但也有一批"顺带读一下"的地方（提示词拼装、格式化、兜底判断）拿不到。
 * 与其到处穿参数、漏一处就悄悄读成全局值，不如在**进入某个世界的作业时**把它的配置放进作用域，
 * 读取时统一先看作用域、再回落到全局 sys_config、最后回落到代码默认值。
 *
 * 使用纪律（与 {@link com.bc.bcblog.component.AuditContext} 一样）：
 *   begin(...) 之后必须 end()，所以调用点一律写成 try/finally（服务里统一走 withWorldConfig 包装）。
 *
 * 只有 {@code sandbox_*} 开头的键参与这套机制，其它配置（站点、邮箱、清理天数…）照旧走全局。
 */
public final class SandboxConfigScope {

    private SandboxConfigScope() {
    }

    /** 「AI 调用总闸」：全局唯一，不参与世界级覆盖（一键全停用） */
    public static final String GLOBAL_SWITCH_KEY = "sandbox_enabled";

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    /** 这个键是否属于"可按世界配置"的沙盒参数 */
    public static boolean worldScoped(String key) {
        return key != null && key.startsWith("sandbox_") && !GLOBAL_SWITCH_KEY.equals(key);
    }

    /** 进入某个世界的配置作用域（传入的 map 会被就地修改，调用方负责落库） */
    public static void begin(Map<String, String> settings) {
        HOLDER.set(settings == null ? new HashMap<>() : settings);
    }

    /** 当前作用域里的配置（没有作用域时返回 null） */
    public static Map<String, String> current() {
        return HOLDER.get();
    }

    /** 是否有世界配置作用域（用于区分"没配过这个世界"和"值就是空的"） */
    public static boolean active() {
        return HOLDER.get() != null;
    }

    /**
     * 取世界级的值；没有作用域或这个世界没配过这个键时返回 null（交给全局配置兜底）。
     * 注意：空串是**有效值**（例如"留空表示关闭"），所以这里不能用 isEmpty 判断。
     */
    public static String get(String key) {
        Map<String, String> settings = HOLDER.get();
        if (settings == null || !worldScoped(key)) {
            return null;
        }
        return settings.get(key);
    }

    /**
     * 写世界级的值。
     *
     * @return true 表示"这次写入已经由世界配置接管了，不要再写全局"；
     *         false 表示键不属于沙盒参数、或当前没有世界作用域，按老逻辑写全局
     */
    public static boolean put(String key, String value) {
        Map<String, String> settings = HOLDER.get();
        if (settings == null || !worldScoped(key)) {
            return false;
        }
        settings.put(key, value);
        return true;
    }

    /** 退出作用域（务必放在 finally 里，线程池复用线程时不清会串到下一个请求） */
    public static void end() {
        HOLDER.remove();
    }
}
