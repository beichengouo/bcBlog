-- 流光忆庭（照片墙）与智库（资源区）建表脚本

CREATE TABLE IF NOT EXISTS `blog_photo` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) DEFAULT NULL COMMENT '照片标题',
    `description` varchar(500) DEFAULT NULL COMMENT '简单介绍',
    `url` varchar(500) NOT NULL COMMENT '图片地址',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流光忆庭照片';

CREATE TABLE IF NOT EXISTS `blog_resource` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL COMMENT '资源名称',
    `description` varchar(500) DEFAULT NULL COMMENT '资源说明',
    `url` varchar(500) NOT NULL COMMENT '资源链接',
    `password` varchar(100) DEFAULT NULL COMMENT '提取密码',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智库资源';
