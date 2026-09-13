package com.bc.bcblog.init;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.AiProviderMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final AiProviderMapper aiProviderMapper;

    @Override
    public void run(String... args) {
        seedAdmin();
        try {
            seedDefaultAiProvider();
        } catch (Exception e) {
            // 兼容旧库/新库还没有 ai_provider 表的情况，不阻塞项目启动
            log.warn("初始化默认 AI 服务商失败（可能是数据表尚未创建）：{}", e.getMessage());
        }
    }

    /** 创建默认超级管理员。 */
    private void seedAdmin() {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "admin"));
        if (count != null && count > 0) {
            return;
        }

        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword(BCrypt.hashpw("Admin@123456"));
        user.setNickname("管理员");
        user.setStatus(1);
        user.setRole("SUPER");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        log.info("已创建默认管理员账号：admin / Admin@123456，请登录后尽快修改密码");
    }

    /**
     * 首次启动时把“硅基流动 DeepSeek”作为默认 AI 服务商写入，
     * 让 AI 写文章默认走该免费接口；管理员仍可在“AI 服务商”中切换为其他接口。
     * 如果已经存在硅基流动服务商，则不做任何修改，避免覆盖管理员后续的选择。
     */
    private void seedDefaultAiProvider() {
        String baseUrl = "https://api.siliconflow.cn/v1";
        Long existing = aiProviderMapper.selectCount(new LambdaQueryWrapper<AiProvider>()
                .like(AiProvider::getBaseUrl, "api.siliconflow.cn"));
        if (existing != null && existing > 0) {
            return;
        }

        AiProvider provider = new AiProvider();
        provider.setName("DeepSeek（硅基流动）");
        provider.setBaseUrl(baseUrl);
        // API Key 需要管理员在“AI 服务商”页面填写自己的 SiliconFlow Token
        provider.setApiKey("");
        provider.setIsDefault(1);
        provider.setCreateTime(LocalDateTime.now());
        aiProviderMapper.insert(provider);

        // 新服务商成为默认，同时清掉其他服务商的默认标记
        aiProviderMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .ne(AiProvider::getId, provider.getId())
                .set(AiProvider::getIsDefault, 0));
        log.info("已创建默认 AI 服务商：DeepSeek（硅基流动），请前往 AI 服务商页面配置 Token");
    }
}
