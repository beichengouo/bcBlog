-- ============================================================================
-- 沙盒世界：由 AI 决定下一次行动间隔
-- ----------------------------------------------------------------------------
-- 背景：固定 45~75 分钟会出现「刚睡下又被叫醒」的不合理情况。
--       现在每次行动时由 AI 自己给出「下次隔多久再行动」，服务端按它排期。
--
-- 本次改动：
--   1. sandbox_act 增加 next_after_minutes / next_after_reason：
--      记录这一步之后 AI 期望的间隔与原因（如「睡觉」），后台日志可查看。
--   2. sandbox_character 增加 next_reason（前台显示「正在睡觉，约 6 小时后」）
--      与 ai_interval_min / ai_interval_max（角色级覆盖，留空则用全局设置）。
--   3. 新增全局配置：是否启用 AI 间隔、间隔上下限（默认 15 ~ 720 分钟）。
--   4. 夜间静默默认关闭（起止时间相同即为关闭）：因为角色自己会去睡觉，
--      不再需要额外的静默时段。如仍想启用，在后台把两个时间改成不同值即可。
--   5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bcblog_add_col('sandbox_act', 'next_after_minutes',
    'int NOT NULL DEFAULT 0 COMMENT ''这一步之后 AI 期望的间隔分钟数，0 表示未指定'' AFTER `summary`');

CALL bcblog_add_col('sandbox_act', 'next_after_reason',
    'varchar(50) DEFAULT NULL COMMENT ''间隔原因，如「睡觉」「赶路」'' AFTER `next_after_minutes`');

CALL bcblog_add_col('sandbox_character', 'next_reason',
    'varchar(50) DEFAULT NULL COMMENT ''下次行动的原因，如「睡觉」，前台展示用'' AFTER `next_run_time`');

CALL bcblog_add_col('sandbox_character', 'ai_interval_min',
    'int DEFAULT NULL COMMENT ''该角色 AI 间隔下限（分钟），留空用全局设置'' AFTER `interval_max`');

CALL bcblog_add_col('sandbox_character', 'ai_interval_max',
    'int DEFAULT NULL COMMENT ''该角色 AI 间隔上限（分钟），留空用全局设置'' AFTER `ai_interval_min`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_ai_interval_enabled', '1', '是否由 AI 决定下次行动间隔：1 启用，0 用固定的随机区间'),
    ('sandbox_ai_interval_min', '15', 'AI 间隔下限（分钟）'),
    ('sandbox_ai_interval_max', '720', 'AI 间隔上限（分钟），720 = 12 小时');

-- 夜间静默默认关闭（起止时间相同 = 不启用）
UPDATE `sys_config` SET `config_value` = '00:00'
WHERE `config_key` IN ('sandbox_night_start', 'sandbox_night_end');

SELECT config_key, config_value FROM `sys_config`
WHERE config_key LIKE 'sandbox_ai_interval%' OR config_key LIKE 'sandbox_night%'
ORDER BY config_key;
