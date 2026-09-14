-- 用户体系、等级、签到、邀请码、原生评论

ALTER TABLE `sys_user`
    ADD COLUMN `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    ADD COLUMN `exp` int NOT NULL DEFAULT 0 COMMENT '经验值',
    ADD COLUMN `level` int NOT NULL DEFAULT 1 COMMENT '等级',
    ADD COLUMN `can_invite` tinyint NOT NULL DEFAULT 0 COMMENT '是否有邀请码权限',
    ADD COLUMN `sign_days` int NOT NULL DEFAULT 0 COMMENT '累计签到天数',
    ADD COLUMN `last_sign_date` date DEFAULT NULL COMMENT '最后签到日期';

ALTER TABLE `blog_comment`
    ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '登录用户ID',
    ADD COLUMN `avatar` varchar(500) DEFAULT NULL COMMENT '头像快照',
    ADD COLUMN `level` int DEFAULT NULL COMMENT '等级快照',
    ADD COLUMN `level_name` varchar(50) DEFAULT NULL COMMENT '等级名称快照';

CREATE TABLE IF NOT EXISTS `sys_level` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `level` int NOT NULL COMMENT '等级',
    `name` varchar(50) NOT NULL COMMENT '等级名称',
    `exp_required` int NOT NULL COMMENT '达到该等级所需经验',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `color` varchar(20) DEFAULT NULL COMMENT '颜色',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级配置';

CREATE TABLE IF NOT EXISTS `sys_sign_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `sign_date` date NOT NULL COMMENT '签到日期',
    `exp` int NOT NULL DEFAULT 0 COMMENT '获得经验',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录';

CREATE TABLE IF NOT EXISTS `sys_invite_code` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(50) NOT NULL COMMENT '邀请码',
    `creator_id` bigint NOT NULL COMMENT '创建人ID',
    `creator_name` varchar(100) DEFAULT NULL COMMENT '创建人昵称',
    `use_count` int NOT NULL DEFAULT 0 COMMENT '使用次数',
    `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码';

-- 默认等级配置（后台可修改）
INSERT INTO `sys_level` (`level`, `name`, `exp_required`) VALUES
    (1, '初来乍到', 0),
    (2, '渐入佳境', 100),
    (3, '小有名气', 300),
    (4, '活跃之星', 600),
    (5, '资深常客', 1000),
    (6, '意见领袖', 1600),
    (7, '社区骨干', 2400),
    (8, '荣誉元老', 3500),
    (9, '传奇存在', 5000),
    (10, '永恒传说', 7000)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `exp_required` = VALUES(`exp_required`);

-- 默认配置项（已存在则不覆盖）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('comment_system', 'gitalk', '评论系统：gitalk / native'),
    ('register_invite_required', '0', '注册是否需要邀请码'),
    ('register_email_verify', '1', '注册是否需要邮箱验证码'),
    ('sign_exp', '5', '每日签到获得经验'),
    ('comment_exp', '3', '每次评论获得经验'),
    ('comment_exp_limit', '3', '每天获得经验的评论次数上限')
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
