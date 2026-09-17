-- ============================================================================
-- 沙盒角色新增「当前目标」
-- ----------------------------------------------------------------------------
-- 作用：让角色有自己的事要做（去晨雾森林采药、回圣云教国交委托…）。
--       两个角色目标不同时，AI 会自然分头行动，避免"遇到就永远黏在一起"。
--       目标由 AI 在行动里维护（完成就换一个符合人设与处境的新目标），管理员也可在后台直接改。
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

CALL bcblog_add_col('sandbox_character', 'goal',
    'varchar(100) DEFAULT NULL COMMENT ''当前目标（AI 维护，管理员可改）：例如"去晨雾森林采药"'' AFTER `sub_location`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'goal';
