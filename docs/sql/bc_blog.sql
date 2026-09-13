-- bcBlog 数据库初始化脚本
-- 用 Navicat 直接执行本文件即可（会自动建库并创建表）

CREATE DATABASE IF NOT EXISTS `bc_blog` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `bc_blog`;

DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `blog_comment`;
DROP TABLE IF EXISTS `blog_article_tag`;
DROP TABLE IF EXISTS `blog_tag`;
DROP TABLE IF EXISTS `blog_article`;
DROP TABLE IF EXISTS `blog_category`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

CREATE TABLE `blog_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '分类名',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0为顶级',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

CREATE TABLE `blog_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '标签名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

CREATE TABLE `blog_article` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  `content` LONGTEXT COMMENT '正文',
  `cover` VARCHAR(255) DEFAULT NULL COMMENT '封面',
  `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1发布',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '0否 1置顶',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

CREATE TABLE `blog_article_tag` (
  `article_id` BIGINT NOT NULL,
  `tag_id` BIGINT NOT NULL,
  PRIMARY KEY (`article_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';

CREATE TABLE `blog_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `article_id` BIGINT NOT NULL COMMENT '文章ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论ID，0为顶层',
  `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `content` VARCHAR(1000) NOT NULL COMMENT '内容',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(500) DEFAULT NULL COMMENT '配置值',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

CREATE TABLE `sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) DEFAULT NULL COMMENT '用户名',
  `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'UA',
  `success` TINYINT NOT NULL DEFAULT 0 COMMENT '0失败 1成功',
  `message` VARCHAR(200) DEFAULT NULL COMMENT '结果说明',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- 默认管理员由后端首次启动时自动创建（用户名 admin，密码 Admin@123456）
