-- ============================================================================
-- 前台各页面独立背景：首页 / 流光忆庭 / 智库 / 沙盒世界
-- ----------------------------------------------------------------------------
-- 背景：此前只有「前台默认壁纸」（background.portal_active）与「后台壁纸」两种，
--       现在前台主要页面可以各自指定壁纸与不透明度。
--
-- 设计：
--   1. 每个页面一行，mode 有三种：
--        follow = 跟随前台默认壁纸（默认值，等价于以前的行为）
--        none   = 不使用壁纸，回到主题本身的渐变背景
--        custom = 使用 background_id 指定的那张壁纸
--   2. opacity 控制壁纸不透明度（0.10~1.00），方便让主题色透出来；
--   3. 壁纸库仍然是共用的一张表（background），这里只存「哪个页面用哪张、透明度多少」。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_035_page_background.sql
-- 脚本幂等，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `page_background` (
    `page_key` varchar(32) NOT NULL COMMENT '页面标识：home / photos / resources / sandbox',
    `mode` varchar(10) NOT NULL DEFAULT 'follow' COMMENT 'follow=跟随前台默认壁纸，none=不使用壁纸，custom=使用 background_id',
    `background_id` bigint DEFAULT NULL COMMENT 'mode=custom 时使用的壁纸 id',
    `opacity` decimal(3,2) NOT NULL DEFAULT 1.00 COMMENT '壁纸不透明度 0.10~1.00',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台各页面独立背景设置';

-- 自检：表是否就绪
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'page_background';
