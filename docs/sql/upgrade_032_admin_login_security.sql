-- ============================================================================
-- 管理端登录安全：异常登录邮件通知、安全密码、单点登录、会话管理
-- ----------------------------------------------------------------------------
-- 1. sys_user 增加 security_password：独立于登录密码的「安全密码」（BCrypt）。
-- 2. 新表 sys_login_ip：记录每个管理员最近登录的 IP 与地区，用于判断「陌生 IP / 异地登录」，
--    并记录最近一次异常通知时间（同一 IP 24 小时内只通知一次）。
-- 3. 新增配置：总开关（本地开发可关闭）、安全邮箱、深夜时段、异常通知开关。
-- 4. 登录提醒邮件模板场景 login_alert。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bcblog_add_col('sys_user', 'security_password',
    'varchar(100) DEFAULT NULL COMMENT ''安全密码(BCrypt)，用于敏感操作二次验证'' AFTER `password`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

CREATE TABLE IF NOT EXISTS `sys_login_ip` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '管理员ID',
    `ip` varchar(64) NOT NULL COMMENT '登录IP',
    `region` varchar(100) DEFAULT NULL COMMENT 'IP 归属地（仅异常时查询并缓存）',
    `login_count` int NOT NULL DEFAULT 1 COMMENT '该IP登录次数',
    `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
    `last_notify_time` datetime DEFAULT NULL COMMENT '最近一次异常通知时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ip` (`user_id`, `ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录IP记录';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('admin_security_enabled', '1', '登录安全增强总开关：1 开启，0 关闭'),
    ('admin_security_email', '', '安全邮箱：异常登录提醒与安全操作的收件地址'),
    ('admin_login_alert_enabled', '1', '异常登录邮件提醒：1 开启，0 关闭'),
    ('admin_login_alert_night_start', '00:00', '深夜时段开始（该时段登录视为异常）HH:mm'),
    ('admin_login_alert_night_end', '06:00', '深夜时段结束 HH:mm'),
    ('admin_login_alert_fail_times', '3', '连续登录失败几次触发提醒，0 表示不提醒'),
    ('admin_single_login', '1', '单点登录：1 同一管理员只允许一处后台在线，新登录踢掉旧会话');

-- 异常登录提醒邮件模板（可在后台「邮件管理」里编辑或新增多套）
INSERT INTO `sys_email_template`
    (`scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`, `active`)
SELECT 'login_alert', '异常登录提醒', '【{{siteName}}】安全提醒：{{result}}（{{ip}}）', NULL, 0.90,
'<div style="font-family:system-ui,Segoe UI,sans-serif;color:#4a3b46;line-height:1.9;">
  <h2 style="margin:0 0 12px;color:#b3416b;">{{siteName}} 安全提醒</h2>
  <p>检测到一次需要提醒的登录行为：</p>
  <table style="border-collapse:collapse;font-size:14px;">
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">时间</td><td>{{time}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">账号</td><td>{{username}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">结果</td><td>{{result}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">原因</td><td>{{reason}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">IP</td><td>{{ip}}（{{region}}）</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">浏览器</td><td>{{browser}}</td></tr>
  </table>
  <p style="margin-top:14px;color:#c44b7a;">若非本人操作，请立即修改登录密码与安全密码，并检查后台配置。</p>
</div>', '{{siteName}},{{time}},{{username}},{{result}},{{reason}},{{ip}},{{region}},{{browser}}', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_email_template` t WHERE t.`scenario` = 'login_alert');

SELECT config_key, config_value FROM `sys_config` WHERE config_key LIKE 'admin_%' ORDER BY config_key;
