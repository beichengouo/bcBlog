package com.bc.bcblog.init;

import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.mapper.AiProviderMapper;
import com.bc.bcblog.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把已有的明文密钥加密回写（sys_config 里的密钥项 + ai_provider.api_key）。
 * 幂等：已经加密的值不会被重复处理。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SecretMigrationRunner implements CommandLineRunner {

    private final SysConfigMapper configMapper;
    private final AiProviderMapper providerMapper;
    private final SecretCipher secretCipher;

    @Override
    public void run(String... args) {
        int migrated = 0;
        List<String> secretKeys = SecretCipher.secretConfigKeys();
        for (SysConfig config : configMapper.selectList(null)) {
            if (!SecretCipher.isSecretConfigKey(config.getConfigKey())) {
                continue;
            }
            String value = config.getConfigValue();
            if (value == null || value.isEmpty() || secretCipher.isEncrypted(value)) {
                continue;
            }
            config.setConfigValue(secretCipher.encrypt(value));
            configMapper.updateById(config);
            migrated++;
        }
        for (AiProvider provider : providerMapper.selectList(null)) {
            String key = provider.getApiKey();
            if (key == null || key.isEmpty() || secretCipher.isEncrypted(key)) {
                continue;
            }
            provider.setApiKey(secretCipher.encrypt(key));
            providerMapper.updateById(provider);
            migrated++;
        }
        if (migrated > 0) {
            log.info("已把 {} 处明文密钥加密存储（密钥项：{}）", migrated, secretKeys);
        }
    }
}
