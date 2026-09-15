-- ============================================================================
-- 沙盒世界（AI 角色自动生活）模块
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 脚本可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
--   2. 默认关闭沙盒的 AI 调用（sandbox_enabled = 0），需要管理员在后台开启。
--   3. 地图、地点、角色全部由管理员在后台自行添加，脚本不预置任何角色数据。
--
-- 建议执行顺序：先执行 upgrade_20260915_batch.sql，再执行本脚本。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- ----------------------------------------------------------------------------
-- 1. 世界（地图与世界观，一个世界对应一张地图）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_world` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL DEFAULT '' COMMENT '世界名称',
    `description` varchar(500) DEFAULT NULL COMMENT '世界简介（前台展示）',
    `map_image` varchar(500) DEFAULT NULL COMMENT '地图背景图地址',
    `world_prompt` text COMMENT '世界设定：写给 AI 的世界观、规则与文风',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒世界';

-- ----------------------------------------------------------------------------
-- 2. 地点（坐标使用百分比，0~100，方便适配任意尺寸的地图背景图）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_location` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `name` varchar(100) NOT NULL COMMENT '地点名称',
    `x` int NOT NULL DEFAULT 50 COMMENT '横向坐标百分比 0~100',
    `y` int NOT NULL DEFAULT 50 COMMENT '纵向坐标百分比 0~100',
    `description` varchar(500) DEFAULT NULL COMMENT '地点描述，会作为 AI 行动参考',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world` (`world_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒地图地点';

-- ----------------------------------------------------------------------------
-- 3. 角色（人设、立绘、AI 接口绑定、当前位置与状态）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_character` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `name` varchar(100) NOT NULL COMMENT '角色名',
    `title` varchar(100) DEFAULT NULL COMMENT '称号',
    `avatar` varchar(500) DEFAULT NULL COMMENT '头像 / 立绘地址',
    `appearance` varchar(500) DEFAULT NULL COMMENT '外貌描述（前台展示）',
    `persona` text COMMENT '人设提示词，写法参考酒馆角色卡',
    `provider_id` bigint DEFAULT NULL COMMENT '绑定 AI 服务商ID',
    `model` varchar(100) DEFAULT NULL COMMENT '使用的模型',
    `temperature` decimal(3,2) NOT NULL DEFAULT 0.90 COMMENT '采样温度 0~2',
    `x` int NOT NULL DEFAULT 50 COMMENT '当前横向坐标百分比',
    `y` int NOT NULL DEFAULT 50 COMMENT '当前纵向坐标百分比',
    `location_name` varchar(100) DEFAULT NULL COMMENT '当前位置名称',
    `status_json` varchar(1000) DEFAULT NULL COMMENT '当前状态（JSON，内容由 AI 生成）',
    `next_run_time` datetime DEFAULT NULL COMMENT '下次 AI 行动时间',
    `last_run_time` datetime DEFAULT NULL COMMENT '上次 AI 行动时间',
    `interval_min` int NOT NULL DEFAULT 45 COMMENT '行动间隔最小值（分钟）',
    `interval_max` int NOT NULL DEFAULT 75 COMMENT '行动间隔最大值（分钟）',
    `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次调用失败原因',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world` (`world_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色';

-- ----------------------------------------------------------------------------
-- 4. 行动记录（每小时左右产生一条，前台时间线展示）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_act` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `location_name` varchar(100) DEFAULT NULL COMMENT '这一步所处地点',
    `x` int DEFAULT NULL COMMENT '这一步的横向坐标',
    `y` int DEFAULT NULL COMMENT '这一步的纵向坐标',
    `actions` varchar(1000) DEFAULT NULL COMMENT '做了什么（多条用换行分隔）',
    `inner_voice` varchar(1000) DEFAULT NULL COMMENT '心声',
    `status_json` varchar(1000) DEFAULT NULL COMMENT '这一步结束后的状态',
    `summary` varchar(300) DEFAULT NULL COMMENT '一句话概括',
    `raw_response` text COMMENT 'AI 原始返回，便于排查问题',
    `from_ai` tinyint NOT NULL DEFAULT 1 COMMENT '是否来自 AI：1 是，0 为兜底记录',
    `manual` tinyint NOT NULL DEFAULT 0 COMMENT '是否管理员手动执行：1 是（不占用每日额度）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character_time` (`character_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒行动记录';

-- ----------------------------------------------------------------------------
-- 5. 旅人低语（前台互动：必须登录并消耗积分，会被下一次 AI 行动参考）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_interaction` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `user_id` bigint NOT NULL COMMENT '留言用户ID',
    `user_name` varchar(100) DEFAULT NULL COMMENT '用户昵称快照',
    `user_avatar` varchar(500) DEFAULT NULL COMMENT '用户头像快照',
    `content` varchar(500) NOT NULL COMMENT '低语内容',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '消耗积分',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character` (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人低语';

-- ----------------------------------------------------------------------------
-- 6. 沙盒配置项（默认关闭调用，可在后台「沙盒日志 → 沙盒设置」里修改）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_enabled', '0', '沙盒 AI 调用总开关：1 开启，0 关闭'),
    ('sandbox_interval_min', '45', '角色行动间隔最小值（分钟）'),
    ('sandbox_interval_max', '75', '角色行动间隔最大值（分钟）'),
    ('sandbox_night_start', '02:00', '夜间静默开始时间 HH:mm，期间不调用 AI'),
    ('sandbox_night_end', '07:00', '夜间静默结束时间 HH:mm'),
    ('sandbox_daily_limit', '12', '每个角色每天最多自动调用次数'),
    ('sandbox_whisper_points', '1', '旅人低语每次消耗积分');

-- ----------------------------------------------------------------------------
-- 7. 自检
-- ----------------------------------------------------------------------------
SELECT TABLE_NAME AS '沙盒相关表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sandbox_world', 'sandbox_location', 'sandbox_character', 'sandbox_act', 'sandbox_interaction')
ORDER BY TABLE_NAME;
