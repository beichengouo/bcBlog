-- ============================================================
-- 068 沙盒：世界级的「魔力」条名称
--
-- 背景：状态里的「魔力」是硬编码的，只适合剑与魔法世界观。
--   修仙世界该叫「灵力」、现代世界可能叫「精力」，甚至压根没有这条属性。
--   前台本来就是「状态里有什么键就显示什么」，所以只要让 AI 按世界的叫法输出这个键即可。
--
-- 做法：名称挂在**世界**上（世界本身就是世界观的边界），存储键跟着名字走。
--   · 没配过（NULL）→ 按默认「魔力」处理，老世界行为不变；
--   · 留空字符串（''）→ 这个世界没有这条属性：提示词不再要求输出、服务端会把残留的键清掉、
--     前台也不再显示这一条。
--   历史数据不用迁移：这条键是每次行动由 AI 重新输出的，下一个行动自然就换成新名字了。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL `bcblog_add_col`('sandbox_world', 'mana_label',
    'VARCHAR(20) NULL COMMENT ''「魔力」条在本世界的叫法：留空=没有这条属性，NULL=按默认「魔力」''');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 已有世界统一给上默认值，避免出现"半配"状态（要关掉这条属性的世界，把值清空即可）
UPDATE `sandbox_world` SET `mana_label` = '魔力' WHERE `mana_label` IS NULL;
