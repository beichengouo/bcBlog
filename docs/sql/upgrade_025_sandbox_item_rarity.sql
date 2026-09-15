-- ============================================================================
-- 沙盒角色背包：物品品质与图标
-- ----------------------------------------------------------------------------
-- 1. sandbox_item 增加 rarity（品质 1~5）与 icon（自定义图标图片地址）。
--    前台背包按品质显示不同颜色边框，未上传图标时按物品名自动匹配一个 emoji 图标。
-- 2. AI 新获得的物品会按关键词推断一个初始品质（普通/精良/稀有/史诗/传说），管理员可随时修改。
-- 3. 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_item', 'rarity',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说'' AFTER `quantity`');

CALL bcblog_add_col('sandbox_item', 'icon',
    'varchar(500) DEFAULT NULL COMMENT ''物品图标图片地址，为空时前台按物品名自动匹配图标'' AFTER `rarity`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_item'
  AND COLUMN_NAME IN ('rarity', 'icon')
ORDER BY COLUMN_NAME;
