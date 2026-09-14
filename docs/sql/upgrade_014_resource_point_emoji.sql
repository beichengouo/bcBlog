-- 智库资源详情/封面/积分解锁、表情包

ALTER TABLE `blog_resource`
    ADD COLUMN `cover` varchar(500) DEFAULT NULL COMMENT '封面图' AFTER `description`,
    ADD COLUMN `points` int NOT NULL DEFAULT 1 COMMENT '前往资源所需积分' AFTER `cover`,
    ADD COLUMN `content` text COMMENT '资源详情内容（HTML）' AFTER `points`;

CREATE TABLE IF NOT EXISTS `sys_resource_unlock` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `resource_id` bigint NOT NULL COMMENT '资源ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源解锁记录';

CREATE TABLE IF NOT EXISTS `sys_emoji` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `pack` varchar(100) DEFAULT NULL COMMENT '表情包名称',
    `name` varchar(100) DEFAULT NULL COMMENT '表情名称',
    `url` varchar(500) NOT NULL COMMENT '表情图片地址',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pack` (`pack`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包';
