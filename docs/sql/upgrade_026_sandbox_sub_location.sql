-- ============================================================================
-- 沙盒世界：二级地点
-- ----------------------------------------------------------------------------
-- 1. 角色每次行动时，除了一级地点（后台维护、带坐标），还会让 AI 自行创作一个
--    具体的二级地点，例如「自由城邦联盟 · 东侧集市」。
-- 2. sandbox_act 记录这一步的二级地点；sandbox_character 记录角色当前所在的二级地点。
-- 3. 二级地点不单独占地图坐标，地图上角色位置仍按一级地点绘制。
-- 4. 二级地点会写进每日记忆与其它角色看到的「最近的动静」，让世界更连贯。
-- 5. 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_act', 'sub_location',
    'varchar(100) DEFAULT NULL COMMENT ''二级地点，AI 自行创作，如「东侧集市」'' AFTER `location_name`');

CALL bcblog_add_col('sandbox_character', 'sub_location',
    'varchar(100) DEFAULT NULL COMMENT ''当前所在的二级地点'' AFTER `location_name`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT TABLE_NAME AS '表', COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'sub_location'
  AND TABLE_NAME IN ('sandbox_act', 'sandbox_character')
ORDER BY TABLE_NAME;
