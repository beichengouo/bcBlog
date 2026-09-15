-- ============================================================================
-- 沙盒地图地点：新增图标字段
-- ----------------------------------------------------------------------------
-- icon 存两种值：
--   1. 内置图标 key，例如 pin / village / forest / tower / lake / ruins / tavern
--   2. 管理员自行上传的图片地址，例如 /uploads/xxx.png
-- 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_location', 'icon',
    'varchar(500) DEFAULT NULL COMMENT ''地点图标：内置图标 key 或上传的图片地址'' AFTER `name`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_location' AND COLUMN_NAME = 'icon';
