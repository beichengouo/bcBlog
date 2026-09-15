package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.entity.AdminApiKey;
import com.bc.bcblog.mapper.AdminApiKeyMapper;
import com.bc.bcblog.service.AdminKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 管理员个人密钥实现：AES 加密后存 admin_api_key 表。 */
@Service
@RequiredArgsConstructor
public class AdminKeyServiceImpl implements AdminKeyService {

    private final AdminApiKeyMapper mapper;
    private final SecretCipher secretCipher;

    @Override
    public String get(Long adminId, String keyName) {
        if (adminId == null || keyName == null) {
            return "";
        }
        AdminApiKey row = mapper.selectOne(new LambdaQueryWrapper<AdminApiKey>()
                .eq(AdminApiKey::getAdminId, adminId)
                .eq(AdminApiKey::getKeyName, keyName)
                .last("limit 1"));
        if (row == null || row.getKeyValue() == null) {
            return "";
        }
        return secretCipher.decrypt(row.getKeyValue());
    }

    @Override
    public void save(Long adminId, String keyName, String value) {
        if (secretCipher.isUnchanged(value)) {
            return;
        }
        AdminApiKey row = mapper.selectOne(new LambdaQueryWrapper<AdminApiKey>()
                .eq(AdminApiKey::getAdminId, adminId)
                .eq(AdminApiKey::getKeyName, keyName)
                .last("limit 1"));
        String encrypted = secretCipher.encrypt(value.trim());
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            AdminApiKey created = new AdminApiKey();
            created.setAdminId(adminId);
            created.setKeyName(keyName);
            created.setKeyValue(encrypted);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            mapper.insert(created);
        } else {
            row.setKeyValue(encrypted);
            row.setUpdateTime(now);
            mapper.updateById(row);
        }
    }
}
