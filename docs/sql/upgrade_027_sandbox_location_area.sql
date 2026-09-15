-- ============================================================================
-- 沙盒地图：地点从「一个坐标点」改为「一块矩形范围」
-- ----------------------------------------------------------------------------
-- 1. sandbox_location 增加 width / height（百分比），与 x / y（左上角）一起表示区域；
--    width = 0 或 height = 0 时仍按「点」处理。
-- 2. 已有地点以「原坐标为中心」自动生成一个默认区域（12% × 7%，视觉上接近正方形），
--    之后可以在后台拖动/缩放调整。只迁移 width=0 的记录，脚本可重复执行。
-- 3. 角色坐标仍由 AI 给出，但服务端会把它夹紧到所选地点的区域内，
--    需要判断「角色在哪个地点」时，按「坐标落在哪个矩形内」优先匹配。
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

CALL bcblog_add_col('sandbox_location', 'width',
    'int NOT NULL DEFAULT 0 COMMENT ''区域宽度百分比；0 表示单点'' AFTER `y`');

CALL bcblog_add_col('sandbox_location', 'height',
    'int NOT NULL DEFAULT 0 COMMENT ''区域高度百分比；0 表示单点'' AFTER `width`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 把还是「点」的地点以原坐标为中心扩成默认区域（只影响 width=0 的记录）
UPDATE `sandbox_location`
SET `width` = 12,
    `height` = 7,
    `x` = GREATEST(0, LEAST(100 - 12, `x` - 6)),
    `y` = GREATEST(0, LEAST(100 - 7, `y` - 4))
WHERE `width` = 0;

SELECT id, name, x, y, width, height FROM `sandbox_location` ORDER BY sort_order, id;
