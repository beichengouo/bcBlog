package com.bc.bcblog.common;

import org.springframework.util.AntPathMatcher;

/**
 * 后台「安全密码」二次验证的路径规则。
 *
 * 单独抽出来是为了能写单测：这条规则一旦漏掉某个接口，就等于那道门形同虚设；
 * 一旦误伤（例如把只读接口也拦了），后台会到处弹验证框。
 */
public final class SecurityPathRules {

    /**
     * 用 Spring 自带的 Ant 通配匹配器。
     * 不用 Sa-Token 的 SaRouter.isMatch 是因为它要求存在 Web 上下文，脱离请求就没法单测；
     * 这里的规则恰恰最需要单测（漏一条 = 门没锁，多一条 = 后台到处弹框）。
     */
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 需要「安全密码」二次验证的敏感接口。
     *
     * 教训：以前只列了 user/member/invite 几个路径，沙盒世界、系统设置这些菜单并没有被保护，
     * 而页面上恰好有个「AI 服务商列表」接口在保护名单里，于是会出现
     * "弹了个二次验证的框，点空白处关掉之后，页面数据照样加载"的假象。
     * 现在把会碰到密钥、其他用户数据、AI 消耗、站点配置的后台接口都列进来。
     */
    public static final String[] PROTECTED_PATHS = {
            "/api/admin/user/**",
            "/api/admin/member/**",
            "/api/admin/invite/**",
            "/api/admin/gitalk/**",
            "/api/admin/email/**",
            "/api/admin/deepseek/**",
            "/api/admin/ai/**",
            "/api/admin/acg-cover/**",
            "/api/admin/ip-location/**",
            // 站点设置：站点名、Logo、备案、SEO、注册规则、清理策略等
            "/api/admin/config",
            "/api/admin/config/logo",
            "/api/admin/config/ip-location-provider/**",
            // 上面那三把写操作 Key（父路径已覆盖，这里列出来让"哪些 Key 受保护"一眼可见）
            "/api/admin/config/ip-location-ak/**",
            "/api/admin/config/gaode-ip-key/**",
            "/api/admin/config/acg-cover-token/**",
            // 沙盒世界：世界/角色/行动/记忆/关系/背包/集市/旅人纪闻
            "/api/admin/sandbox/**",
            // 登录日志、API 调用审计
            "/api/admin/log/**",
            "/api/admin/audit/**",
            // 积分发放与等级配置
            "/api/admin/point/**",
            "/api/admin/level/**",
            // 数据清理
            "/api/admin/system/cleanup"
    };

    /**
     * 只拦「写」操作的路径：GET 放行。
     *
     * 站点设置本体(/api/admin/config)到处被读——后台壁纸层、看板娘管理、邮件管理都要读它，
     * 如果连读也要求二次验证，等于每翻一页都弹框；而且读到的内容没有敏感信息（前台接口本来就公开）。
     * 真正需要保护的是"改了配置"这个动作，所以只有 PUT/POST/DELETE 才要安全密码。
     */
    public static final String[] WRITE_ONLY_PATHS = {
            "/api/admin/config",
            "/api/admin/config/logo"
    };

    private SecurityPathRules() {
    }

    /** 该请求是否要安全密码二次验证 */
    public static boolean needVerify(String path, String method) {
        if (!matchAny(PROTECTED_PATHS, path)) {
            return false;
        }
        if (matchAny(WRITE_ONLY_PATHS, path)) {
            return !"GET".equalsIgnoreCase(method);
        }
        return true;
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
