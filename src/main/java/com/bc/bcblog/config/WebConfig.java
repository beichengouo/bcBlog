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
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

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
    };

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
