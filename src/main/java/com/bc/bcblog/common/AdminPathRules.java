package com.bc.bcblog.common;

import org.springframework.util.AntPathMatcher;

/**
 * 后台接口的权限路径规则（超级管理员专属 / 菜单授权 / 公共能力）。
 *
 * 抽出来的原因和 SecurityPathRules 一样：这条规则一错就是安全边界出错
 * （该锁的没锁 → 越权；不该锁的锁了 → 被授权的菜单打不开、满屏"没有该菜单的权限"）。
 * 用 Spring 的 AntPathMatcher 而不是 Sa-Token 的匹配，是为了脱离 Web 上下文也能单测。
 *
 * 语义提醒：AntPathMatcher 里 {@code /a/**} 能匹配 {@code /a} 与 {@code /a/b}，
 * **但不会**匹配 {@code /ab}——所以「/api/admin/sandbox/world/**」不会误伤「/api/admin/sandbox/worlds」。
 */
public final class AdminPathRules {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 仅超级管理员可用（任何请求方法都拦）。
     *
     * 判断标准：这个功能用到了**系统级密钥**、**别的账号**或**破坏性操作**。
     * 普通管理员即使被授权了对应菜单，接口也一律 403 并记一条越权审计。
     */
    public static final String[] SUPER_ONLY_PATHS = {
            // 管理账号 / 其他用户数据
            "/api/admin/user/**",
            "/api/admin/member/**",
            "/api/admin/invite/**",
            // 系统级密钥：GitHub Token、QQ 邮箱授权码、DeepSeek Key、地图与 ACG Token
            "/api/admin/gitalk/**",
            "/api/admin/email/**",
            "/api/admin/deepseek/**",
            "/api/admin/ip-location/**",
            "/api/admin/acg-cover/**",
            "/api/admin/config/ip-location-ak/**",
            "/api/admin/config/gaode-ip-key/**",
            "/api/admin/config/acg-cover-token/**",
            "/api/admin/config/ip-location-provider/**",
            // 审计与安全
            "/api/admin/audit/**",
            "/api/admin/security/**",
            "/api/admin/log/**",
            // 沙盒「世界与地图」：世界/地点的增删改、世界启停、导出导入、清空
            // （世界列表 /api/admin/sandbox/worlds 除外：其它沙盒页面的世界下拉框要用）
            "/api/admin/sandbox/world",
            "/api/admin/sandbox/world/**",
            // 破坏性：数据清理
            "/api/admin/system/cleanup"
    };

    /**
     * 只对**写操作**（非 GET）要求超级管理员。
     *
     * 这些接口的"读"被多个页面共用（例如集市管理与行动日志都要读一遍沙盒设置），
     * 一刀切会让被授权的页面直接 403；而"写"才是真正敏感的部分
     * （写它们的页面已经改为调用各自作用域的接口，例如 /sandbox/shop/settings）。
     */
    public static final String[] SUPER_ONLY_WRITE_PATHS = {
            "/api/admin/sandbox/settings",
            "/api/admin/sandbox/locations",
            "/api/admin/sandbox/locations/**"
    };

    /**
     * 不参与「菜单授权」校验的公共读接口：任何已登录的后台管理员都能调用。
     *
     * 背景：站点设置、后台壁纸透明度、系统监控、模型下拉框这些数据被多个页面共用，
     * 但按菜单映射它们属于「系统设置 / 背景管理 / API 管理」，
     * 于是被授权了别的菜单（例如集市管理）的管理员一进页面就会看到
     * "没有该菜单的权限"——页面本身却能正常用，非常莫名其妙。
     */
    public static final String[] MENU_EXEMPT_READ_PATHS = {
            // 读站点设置：后台壁纸层、看板娘管理、邮件管理、系统设置都要读（写仍归「系统设置」菜单）
            "/api/admin/config",
            // 后台壁纸透明度：后台每个页面都会读它
            "/api/admin/config/admin-bg-opacity",
            // 仪表盘的系统信息
            "/api/admin/system/monitor",
            // 模型下拉框：沙盒角色/行动日志/集市管理都要用它选模型
            "/api/admin/ai/provider/list",
            "/api/admin/ai/provider/*/models"
    };

    private AdminPathRules() {
    }

    /** 该请求是否仅超级管理员可用 */
    public static boolean isSuperOnly(String path, String method) {
        if (matchAny(SUPER_ONLY_PATHS, path)) {
            return true;
        }
        return isWrite(method) && matchAny(SUPER_ONLY_WRITE_PATHS, path);
    }

    /** 该请求是否可以跳过「菜单授权」校验（公共读接口） */
    public static boolean isMenuExempt(String path, String method) {
        return !isWrite(method) && matchAny(MENU_EXEMPT_READ_PATHS, path);
    }

    private static boolean isWrite(String method) {
        return method != null && !"GET".equalsIgnoreCase(method);
    }

    private static boolean matchAny(String[] patterns, String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
