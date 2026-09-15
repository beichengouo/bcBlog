-- ============================================================================
-- 沙盒世界：旅人纪闻（当天世界大事）
-- ----------------------------------------------------------------------------
-- 1. 新表 sandbox_news：由 AI 独立生成（也可以后台手写）的当天世界事件，
--    前台在地图左上角以「旅人纪闻」栏目展示，只显示当天，第二天自动清理。
-- 2. 角色行动时会在提示词里看到【今日要闻】以及事件与自己的距离，
--    由 AI 自行决定是否参与（通常只是听说、议论、担心）。
-- 3. 新增配置：栏目名称、开关、每次生成条数、生成用的 AI 服务商与模型、附加要求。
-- 4. 保留天数接入数据清理（默认 1 天 = 只留当天）。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_news` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `title` varchar(200) NOT NULL COMMENT '一句话事件，前台展示',
    `content` varchar(500) DEFAULT NULL COMMENT '补充说明',
    `location_name` varchar(100) DEFAULT NULL COMMENT '事件发生地点',
    `x` int DEFAULT NULL COMMENT '事件坐标 X（用于计算与角色的距离）',
    `y` int DEFAULT NULL COMMENT '事件坐标 Y',
    `level` tinyint NOT NULL DEFAULT 1 COMMENT '重要度：1 普通 / 2 重要 / 3 重大',
    `source` varchar(20) NOT NULL DEFAULT 'ai' COMMENT '来源：ai 自动生成 / admin 管理员添加',
    `news_date` date NOT NULL COMMENT '归属日期（只展示当天）',
    `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_date` (`news_date`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人纪闻';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_news_title', '旅人纪闻', '前台地图左上角栏目的名称'),
    ('sandbox_news_enabled', '1', '是否启用旅人纪闻：1 启用，0 关闭'),
    ('sandbox_news_per_generate', '3', '每次生成几条事件'),
    ('sandbox_news_provider_id', '', '生成事件使用的 AI 服务商 ID，留空用默认服务商'),
    ('sandbox_news_model', '', '生成事件使用的模型，留空则生成前需手动选择'),
    ('sandbox_news_prompt_extra', '', '生成事件时的附加要求（例如偏向节庆、灾祸、商队等）'),
    ('cleanup_sandbox_news_days', '1', 'sandbox_news 旅人纪闻保留天数（1 = 只留当天）');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_news';
