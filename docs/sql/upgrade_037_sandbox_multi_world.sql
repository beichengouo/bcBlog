-- ============================================================================
-- 沙盒：支持多个世界（信息互不共享）+ 旅人低语总开关
-- ----------------------------------------------------------------------------
-- 背景：此前沙盒只有一个世界（world 表里取第一条），角色/地点/行动虽然都带 world_id，
--       但查询没有按 world 过滤，后台与前台也没有世界选择。
--
-- 本次改动：
--   1. sandbox_world.portal_visible：前台是否可见。和原有的 enabled（是否运行）是两个独立开关：
--        enabled=1 才会自动行动；portal_visible=1 才会出现在前台世界下拉里（只停跑也允许前台看历史）；
--   2. sandbox_interaction（旅人低语）与 sandbox_coin_log（金币流水）补 world_id，
--      老数据统一归到现有世界，这样删除世界时可以准确级联清理；
--   3. 新增配置 sandbox_whisper_enabled：旅人低语总开关（默认开启，关闭后前台隐藏入口）。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_037_sandbox_multi_world.sql
-- 脚本幂等，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE `bcblog_add_idx`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 1. 世界：前台是否可见（与「是否运行」独立）
CALL bcblog_add_col('sandbox_world', 'portal_visible',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''前台是否可见：1 出现在前台世界下拉（可只看历史），0 完全隐藏'' AFTER `enabled`');

-- 2. 旅人低语 / 金币流水补世界归属
CALL bcblog_add_col('sandbox_interaction', 'world_id',
    'bigint DEFAULT NULL COMMENT ''所属世界'' AFTER `character_id`');
CALL bcblog_add_col('sandbox_coin_log', 'world_id',
    'bigint DEFAULT NULL COMMENT ''所属世界'' AFTER `character_id`');

-- 老数据统一归到现有世界（取 id 最小的那个）
UPDATE `sandbox_interaction`
SET `world_id` = (SELECT `id` FROM `sandbox_world` ORDER BY `id` LIMIT 1)
WHERE `world_id` IS NULL AND EXISTS (SELECT 1 FROM `sandbox_world`);

UPDATE `sandbox_coin_log`
SET `world_id` = (SELECT `id` FROM `sandbox_world` ORDER BY `id` LIMIT 1)
WHERE `world_id` IS NULL AND EXISTS (SELECT 1 FROM `sandbox_world`);

CALL bcblog_add_idx('sandbox_interaction', 'idx_world', 'KEY `idx_world` (`world_id`)');
CALL bcblog_add_idx('sandbox_coin_log', 'idx_world', 'KEY `idx_world` (`world_id`)');

-- 3. 旅人低语总开关
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_whisper_enabled', '1', '旅人低语总开关：1 开启（前台可给角色留言），0 关闭（前台隐藏入口，接口同时拦截，历史数据保留）');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;

-- 自检
SELECT TABLE_NAME AS '表', COLUMN_NAME AS '字段', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'sandbox_world' AND COLUMN_NAME = 'portal_visible')
    OR (TABLE_NAME IN ('sandbox_interaction', 'sandbox_coin_log') AND COLUMN_NAME = 'world_id'))
ORDER BY TABLE_NAME;

SELECT config_key AS '配置项', config_value AS '当前值', remark AS '说明'
FROM `sys_config` WHERE config_key = 'sandbox_whisper_enabled';
