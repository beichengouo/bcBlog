-- 邮件配置与邮件模板（QQ 邮箱 SMTP 模式）

CREATE TABLE IF NOT EXISTS `sys_email_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `scenario` varchar(50) NOT NULL COMMENT '场景编码，如 register_code',
    `name` varchar(100) NOT NULL COMMENT '模板名称',
    `subject` varchar(200) NOT NULL COMMENT '邮件主题',
    `background_image` varchar(500) DEFAULT NULL COMMENT '背景图片地址',
    `overlay_opacity` decimal(3,2) NOT NULL DEFAULT 0.85 COMMENT '内容卡片背景透明度 0.1~1',
    `content_html` text COMMENT '正文 HTML，支持 {{变量}}',
    `variables` varchar(500) DEFAULT NULL COMMENT '可用变量说明',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scenario` (`scenario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件模板';

-- QQ 邮箱 SMTP 配置（账号和授权码请在后台填写）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('email_username', '', 'QQ 邮箱账号'),
    ('email_auth_code', '', 'QQ 邮箱授权码'),
    ('email_sender_name', 'bcBlog', '发件人昵称'),
    ('email_host', 'smtp.qq.com', 'SMTP 服务器'),
    ('email_port', '465', 'SMTP 端口'),
    ('email_ssl', '1', '是否使用 SSL')
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;

-- 默认注册验证码模板
INSERT INTO `sys_email_template`
    (`scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`)
VALUES
    ('register_code', '注册验证码', '【{{siteName}}】邮箱验证码', NULL, 0.90,
     '<h2 style="margin:0 0 16px;color:#333;">{{siteName}} 邮箱验证码</h2><p style="color:#555;line-height:1.8;">你好，你正在注册 {{siteName}} 账号，验证码是：</p><div style="font-size:28px;font-weight:700;letter-spacing:6px;color:#ff6f9f;margin:18px 0;">{{code}}</div><p style="color:#999;font-size:13px;">验证码 5 分钟内有效，请勿泄露给他人。</p>',
     '{{siteName}},{{code}},{{email}},{{nickname}}', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
