-- ============================================================================
-- 沙盒：角色新增「战斗力」
-- ----------------------------------------------------------------------------
-- 设计：
--   1. sandbox_character.combat_power：角色当前战斗力，默认 10，管理员可在后台直接改；
--   2. sandbox_act.combat_change：这一步战斗力的变化（0 表示没变），前台只在变化时展示；
--   3. AI 每次行动会返回 combat_change，只有真正影响实力的事情（学会新魔法、得到强力装备、
--      受伤、长期休养恢复）才给非 0，幅度限制在 ±5 以内，并要求原因写在 actions 里。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_038_sandbox_combat_power.sql
-- 脚本幂等，可重复执行。
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

-- 角色当前战斗力
CALL bcblog_add_col('sandbox_character', 'combat_power',
    'int NOT NULL DEFAULT 10 COMMENT ''战斗力：综合实力（战斗技巧、魔力、装备），默认 10'' AFTER `coins`');

-- 单次行动的战斗力变化（0 表示没变）
CALL bcblog_add_col('sandbox_act', 'combat_change',
    'int NOT NULL DEFAULT 0 COMMENT ''这一步战斗力的变化，0 表示没变'' AFTER `coin_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 老角色统一给 10（新字段默认值已经覆盖，这里只是保险）
UPDATE `sandbox_character` SET `combat_power` = 10 WHERE `combat_power` IS NULL OR `combat_power` < 1;

SELECT TABLE_NAME AS '表', COLUMN_NAME AS '字段', COLUMN_TYPE AS '类型', COLUMN_DEFAULT AS '默认值'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'combat_power')
    OR (TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'combat_change'))
ORDER BY TABLE_NAME;
