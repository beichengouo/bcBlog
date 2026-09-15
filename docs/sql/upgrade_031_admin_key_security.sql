-- ============================================================================
-- 管理端安全加固：AI 服务商归属、按管理员存储的密钥、API 调用审计
-- ----------------------------------------------------------------------------
-- 1. ai_provider 增加 owner_id：NULL = 系统服务商（超管维护，定时任务与超管使用），
--    填写管理员 ID = 该管理员自己的服务商（手动调用时使用自己的）。
-- 2. 新表 admin_api_key：按管理员保存的第三方密钥（如 DeepSeek Key）。
-- 3. 新表 admin_api_log：API 调用审计（谁、什么时候、调用了什么、用了哪个服务商、成败）。
-- 4. 审计日志保留天数接入数据清理（默认 3 天）。
-- 5. 脚本可重复执行。
--
-- 说明：密钥加密由程序处理（AES-256-GCM，主密钥放在与 jar 同级的
--      bcblog-secret.key 文件里）。程序首次启动时会自动把已有的明文密钥加密回写。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bcblog_add_col('ai_provider', 'owner_id',
    'bigint DEFAULT NULL COMMENT ''归属管理员ID，NULL 表示系统服务商'' AFTER `is_default`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

CREATE TABLE IF NOT EXISTS `admin_api_key` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` bigint NOT NULL COMMENT '所属管理员',
    `key_name` varchar(50) NOT NULL COMMENT '密钥名称，如 deepseek_api_key',
    `key_value` varchar(1000) DEFAULT NULL COMMENT '密钥值（程序加密后存储）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_key` (`admin_id`, `key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员个人密钥';

CREATE TABLE IF NOT EXISTS `admin_api_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` bigint DEFAULT NULL COMMENT '调用者ID，定时任务为空',
    `admin_name` varchar(100) DEFAULT NULL COMMENT '调用者用户名/昵称快照',
    `action` varchar(100) NOT NULL COMMENT '动作，如「沙盒·立即执行一次」',
    `source` varchar(20) NOT NULL DEFAULT 'manual' COMMENT 'manual 手动 / schedule 定时',
    `target` varchar(200) DEFAULT NULL COMMENT '使用的服务商或第三方接口（不含密钥）',
    `success` tinyint NOT NULL DEFAULT 1 COMMENT '是否成功',
    `message` varchar(300) DEFAULT NULL COMMENT '失败原因',
    `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
    `ip` varchar(64) DEFAULT NULL COMMENT '调用方 IP',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin` (`admin_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 调用审计';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_admin_api_log_days', '3', 'admin_api_log API 调用审计保留天数');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('admin_api_key', 'admin_api_log')
ORDER BY TABLE_NAME;
