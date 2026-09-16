package com.bc.bcblog.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AuditLogService;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.SecurityPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SysUserMapper sysUserMapper;
    private final AuditLogService auditLogService;
    private final ConfigService configService;
    private final SecurityPasswordService securityPasswordService;

    /**
     * 仅超级管理员可用的后台接口：
     * 这些功能会使用系统级密钥（GitHub Token、邮箱授权码、地图/ACG Token）或管理账号，
     * 一级/二级管理员被授权了对应菜单也不能调用。
     */
    private static final String[] SUPER_ONLY_PATHS = {
            "/api/admin/gitalk/**",
            "/api/admin/email/**",
            "/api/admin/acg-cover/**",
            "/api/admin/ip-location/**",
            "/api/admin/invite/**",
            "/api/admin/user/**",
            "/api/admin/member/**",
            "/api/admin/audit/**",
            "/api/admin/security/**",
            "/api/admin/config/ip-location-ak/**",
            "/api/admin/config/gaode-ip-key/**",
            "/api/admin/config/acg-cover-token/**"
            ,"/api/admin/log/**"
    };

    /** 需要「安全密码」二次验证的敏感操作（本次登录验证过一次即可） */
    private static final String[] SECURITY_PROTECTED_PATHS = {
            "/api/admin/user/**",
            "/api/admin/member/**",
            "/api/admin/invite/**",
            "/api/admin/gitalk/**",
            "/api/admin/email/**",
            "/api/admin/deepseek/**",
            "/api/admin/ai/provider/**",
            "/api/admin/ai/**",
            "/api/admin/acg-cover/**",
            "/api/admin/config/ip-location-ak/**",
            "/api/admin/config/gaode-ip-key/**",
            "/api/admin/config/acg-cover-token/**",
            "/api/admin/system/cleanup"
            // 破坏性操作：删世界 / 清空世界 / 导入并覆盖世界，都要先过安全密码
            ,"/api/admin/sandbox/world/import",
            "/api/admin/sandbox/world/*/reset",
            "/api/admin/sandbox/world/*"
    };

    /**
     * 菜单 key → 后台接口路径前缀。
     * 前端菜单授权（sys_user.menus）以前只影响侧边栏显示，后端不校验，等于"配了也能绕过"；
     * 这里把菜单与接口对应起来：一级/二级管理员访问未授权菜单的接口会直接 403 并记一条越权审计。
     *
     * 说明：
     *   1. 没列进来的路径（上传、仪表盘等公共能力）不做限制，保持原行为；
     *   2. 超级管理员不受这份映射限制；
     *   3. 同一个 key 可以对应多个前缀（例如「沙盒世界」下的四个子菜单共用 /api/admin/sandbox）。 
     */
    private static final Map<String, String[]> MENU_PATHS = new LinkedHashMap<>();

    static {
        MENU_PATHS.put("articles", new String[]{"/api/admin/article/**"});
        MENU_PATHS.put("categories", new String[]{"/api/admin/category/**"});
        MENU_PATHS.put("tags", new String[]{"/api/admin/tag/**"});
        MENU_PATHS.put("comments", new String[]{"/api/admin/comment/**"});
        MENU_PATHS.put("gitalk", new String[]{"/api/admin/gitalk/**"});
        MENU_PATHS.put("photos", new String[]{"/api/admin/photo/**"});
        MENU_PATHS.put("resources", new String[]{"/api/admin/resource/**"});
        MENU_PATHS.put("announcement", new String[]{"/api/admin/announcement/**"});
        MENU_PATHS.put("emoji", new String[]{"/api/admin/emoji/**"});
        // 背景/看板娘页会调用系统设置里的「后台背景透明度」「看板娘开关」，这两个路径也算在它们名下
        MENU_PATHS.put("background", new String[]{"/api/admin/background/**", "/api/admin/config/admin-bg-opacity/**"});
        MENU_PATHS.put("live2d", new String[]{"/api/admin/live2d/**", "/api/admin/config/live2d-enabled"});
        MENU_PATHS.put("music", new String[]{"/api/admin/music/**"});
        // 沙盒四个子菜单共用同一批接口：拥有任意一个子菜单即可访问
        MENU_PATHS.put("sandboxWorld", new String[]{"/api/admin/sandbox/**"});
        MENU_PATHS.put("sandboxCharacters", new String[]{"/api/admin/sandbox/**"});
        MENU_PATHS.put("sandboxActs", new String[]{"/api/admin/sandbox/**"});
        MENU_PATHS.put("sandboxShop", new String[]{"/api/admin/sandbox/**"});
        MENU_PATHS.put("points", new String[]{"/api/admin/point/**"});
        MENU_PATHS.put("levels", new String[]{"/api/admin/level/**"});
        MENU_PATHS.put("logs", new String[]{"/api/admin/log/**"});
        MENU_PATHS.put("settings", new String[]{"/api/admin/config/**", "/api/admin/system/**"});
        MENU_PATHS.put("api", new String[]{"/api/admin/ai/**", "/api/admin/deepseek/**"});
        MENU_PATHS.put("deepseek", new String[]{"/api/admin/deepseek/**"});
        MENU_PATHS.put("third", new String[]{"/api/admin/ip-location/**", "/api/admin/acg-cover/**"});
        MENU_PATHS.put("email", new String[]{"/api/admin/email/**"});
        MENU_PATHS.put("admins", new String[]{"/api/admin/user/**"});
        MENU_PATHS.put("members", new String[]{"/api/admin/member/**"});
        MENU_PATHS.put("invites", new String[]{"/api/admin/invite/**"});
        MENU_PATHS.put("audit", new String[]{"/api/admin/audit/**"});
    }

    /** 本地上传目录，用于把 /uploads/** 映射到磁盘文件 */
    @Value("${bcblog.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle ->
                        SaRouter.match("/api/admin/**").check(r -> {
                            StpUtil.checkLogin();
                            SysUser user = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
                            if (user == null || !isAdminRole(user.getRole())) {
                                throw new BusinessException(403, "无后台访问权限");
                            }
                            // 敏感接口仅超级管理员可用，并记录越权尝试
                            if (!"SUPER".equals(user.getRole())) {
                                String path = SaHolder.getRequest().getRequestPath();
                                for (String pattern : SUPER_ONLY_PATHS) {
                                    if (SaRouter.isMatch(pattern, path)) {
                                        auditLogService.recordDenied("越权尝试", path);
                                        throw new BusinessException(403, "该功能仅超级管理员可用");
                                    }
                                }
                            }
                            // 菜单授权校验：非超管必须拥有该接口对应的菜单
                            if (!"SUPER".equals(user.getRole())) {
                                String path = SaHolder.getRequest().getRequestPath();
                                Set<String> granted = parseMenus(user.getMenus());
                                // 这个接口受菜单管辖、而当前管理员又没有对应菜单 → 拒绝
                                if (matchesAnyMenuPath(path) && !hasAnyMenuAccess(granted, path)) {
                                    auditLogService.recordDenied("越权尝试", path);
                                    throw new BusinessException(403, "没有该菜单的权限，请联系超级管理员授权");
                                }
                            }
                            // 敏感操作需要二次验证（安全密码）；本次登录验证过一次后不再要求
                            if ("1".equals(configService.getConfigValue("admin_security_enabled", "1"))) {
                                String path = SaHolder.getRequest().getRequestPath();
                                for (String pattern : SECURITY_PROTECTED_PATHS) {
                                    if (SaRouter.isMatch(pattern, path) && !securityPasswordService.isVerified()) {
                                        throw new BusinessException(428, "该操作需要输入安全密码验证");
                                    }
                                }
                            }
                        })))
                .addPathPatterns("/**");
    }

    private boolean isAdminRole(String role) {
        return "SUPER".equals(role) || "ADMIN1".equals(role) || "ADMIN2".equals(role);
    }

    /** 解析管理员被授权的菜单 key（数据库里存的是 JSON 数组字符串，如 ["articles","tags"]） */
    /** 这个接口是否受某个菜单管辖（受管辖才需要校验授权） */
    private boolean matchesAnyMenuPath(String path) {
        for (String[] patterns : MENU_PATHS.values()) {
            for (String pattern : patterns) {
                if (SaRouter.isMatch(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> parseMenus(String menus) {
        Set<String> result = new HashSet<>();
        if (menus == null || menus.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray array = JSONUtil.parseArray(menus);
            for (Object item : array) {
                if (item != null) {
                    result.add(String.valueOf(item).trim());
                }
            }
        } catch (Exception e) {
            log.warn("解析管理员菜单授权失败：{}", e.getMessage());
        }
        return result;
    }

    /**
     * 这个接口是否被该管理员的某个已授权菜单管辖。
     * 沙盒这类「多个子菜单共用一批接口」的情况，只要拥有其中一个就算有权限。
     */
    private boolean hasAnyMenuAccess(Set<String> granted, String path) {
        if (granted.isEmpty()) {
            return false;
        }
        for (String key : granted) {
            String[] patterns = MENU_PATHS.get(key);
            if (patterns == null) {
                continue;
            }
            for (String pattern : patterns) {
                if (SaRouter.isMatch(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把上传目录暴露为静态资源，浏览器通过 /uploads/xxx 访问
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location + "/");
    }
}
