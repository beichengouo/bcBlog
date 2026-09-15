package com.bc.bcblog.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 密钥加解密组件（AES-256-GCM）。
 *
 * 主密钥放在与 jar 同级的 bcblog-secret.key 文件里（可用 bcblog.secret-key-file 指定路径）：
 *   - 文件不存在时自动生成一份并写入，同时打印告警，请务必自行备份；
 *   - 文件内容损坏时直接启动失败，避免"以为在加密其实读不出旧数据"。
 *
 * 数据库里存的是 {@code enc:v1:<base64(iv+密文)>}；历史明文仍然可以读取，
 * 程序启动时会自动把已知的明文密钥加密回写。
 *
 * 注意：这种加密能防止数据库导出、SQL 备份泄露导致密钥外流，
 * 但不能防止服务器本身被控制（主密钥与数据在同一台机器上）。
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    /** 表单提交这个标记表示「保持原值不变」 */
    public static final String MASK_FLAG = "****";

    /** 需要加密存储的配置项（sys_config 里的密钥类配置） */
    private static final List<String> SECRET_CONFIG_KEYS = Arrays.asList(
            "deepseek_api_key", "gitalk_token", "email_auth_code",
            "ip_location_ak", "gaode_ip_location_key", "acg_cover_token");

    private final SecretKey secretKey;
    private final String keyFilePath;

    public SecretCipher(@Value("${bcblog.secret-key-file:./bcblog-secret.key}") String configuredPath) {
        Path path = Paths.get(configuredPath).toAbsolutePath().normalize();
        this.keyFilePath = path.toString();
        this.secretKey = loadOrCreateKey(path);
        log.info("密钥加密已启用，主密钥文件：{}（请自行备份，丢失后需要重新填写所有密钥）", keyFilePath);
    }

    /** 该配置项是否需要加密存储 */
    public static boolean isSecretConfigKey(String key) {
        return key != null && SECRET_CONFIG_KEYS.contains(key);
    }

    /** 所有需要加密的配置项 */
    public static List<String> secretConfigKeys() {
        return SECRET_CONFIG_KEYS;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 加密（已是密文或空值则原样返回） */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] data = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + data.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(data, 0, out, iv.length, data.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.error("密钥加密失败：{}", e.getMessage());
            return plain;
        }
    }

    /** 解密（历史明文原样返回；解密失败返回空串并告警，提示需要重新填写） */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length <= IV_LENGTH) {
                return "";
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH);
            byte[] data = Arrays.copyOfRange(raw, IV_LENGTH, raw.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("密钥解密失败（主密钥文件是否更换过？），请在后台重新填写该密钥：{}", e.getMessage());
            return "";
        }
    }

    /** 生成掩码，用于后台回显：sk-****abcd */
    public String mask(String stored) {
        String plain = decrypt(stored);
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.length() <= 8) {
            return MASK_FLAG;
        }
        return plain.substring(0, 4) + MASK_FLAG + plain.substring(plain.length() - 4);
    }

    /** 表单提交的密钥是否需要更新：空值或掩码都表示「保持原值」 */
    public boolean isUnchanged(String submitted) {
        return submitted == null || submitted.trim().isEmpty() || submitted.contains(MASK_FLAG);
    }

    private SecretKey loadOrCreateKey(Path path) {
        try {
            if (Files.exists(path)) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
                byte[] keyBytes = Base64.getDecoder().decode(text);
                if (keyBytes.length != 32) {
                    throw new IllegalStateException("主密钥长度应为 32 字节");
                }
                return new SecretKeySpec(keyBytes, "AES");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            Files.write(path, Base64.getEncoder().encodeToString(generated.getEncoded())
                    .getBytes(StandardCharsets.UTF_8));
            log.warn("未找到主密钥文件，已自动生成：{}。请备份该文件，丢失后所有已加密的密钥都需要重新填写。", path);
            return generated;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取主密钥文件失败：" + path + " - " + e.getMessage(), e);
        }
    }
}
