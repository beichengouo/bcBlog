-- ============================================================================
-- 沙盒世界：好感度结算修正与可选的自查（审查）开关
-- ----------------------------------------------------------------------------
-- 背景：AI 有时给出的 favor_changes 会被服务端截断（例如已到 100 再 +5），
--       旧逻辑把「AI 想要的变化」写进了行动记录，导致记录显示 +5 但好感条没动。
--
-- 本次改动：
--   1. sandbox_relation 增加 last_change / last_change_time，
--      记录「上一次实际生效的变化」，前台可以直接显示「较上次 +3」。
--   2. 新增配置 sandbox_verify_enabled：可选的 AI 自查（审查）开关，
--      开启后每次行动会额外调用一次 AI 只做 JSON 校验与修正，默认关闭（会翻倍消耗 token）。
--   3. 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_relation', 'last_change',
    'int NOT NULL DEFAULT 0 COMMENT ''上一次实际生效的好感度变化'' AFTER `favor`');

CALL bcblog_add_col('sandbox_relation', 'last_change_time',
    'datetime DEFAULT NULL COMMENT ''上一次好感度变化时间'' AFTER `last_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_verify_enabled', '0', 'AI 输出自查开关：1 开启后每次行动额外调用一次 AI 只做 JSON 校验与修正（会翻倍消耗 token）');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_relation'
  AND COLUMN_NAME IN ('last_change', 'last_change_time')
ORDER BY COLUMN_NAME;
