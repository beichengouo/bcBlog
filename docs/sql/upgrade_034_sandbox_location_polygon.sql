-- ============================================================================
-- 沙盒地图地点：支持多边形区域（后台手工套索 / 魔法棒自动描边）
-- ----------------------------------------------------------------------------
-- 背景：地点此前只有「矩形区域」（x/y 左上角 + width/height）。手绘地图里的森林、
--       山脉、海岸线用矩形永远画不贴，所以新增 polygon 字段保存任意多边形区域。
--
-- 设计：
--   1. polygon 存 JSON 顶点数组 [[x,y],[x,y],...]，坐标同样是 0~100 的百分比；
--   2. polygon 为空时，仍然按原来的矩形区域判定 —— 老数据不需要迁移，行为不变；
--   3. 后台进入地点编辑时会把矩形自动转成 4 个顶点，之后保存的就是多边形；
--   4. 区域之间不允许交叉重叠（允许完全包含），校验在保存时做（前后端各一遍）。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_034_sandbox_location_polygon.sql
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

-- 多边形区域顶点（JSON），为空表示继续按矩形区域判定
CALL bcblog_add_col('sandbox_location', 'polygon',
    'text DEFAULT NULL COMMENT ''多边形区域顶点 JSON [[x,y],...]（百分比），为空表示按矩形区域判定'' AFTER `height`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 自检：字段是否就绪
SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_location' AND COLUMN_NAME = 'polygon';
