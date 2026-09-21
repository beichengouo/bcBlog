-- ============================================================
-- 069 沙盒：每个世界一套独立的运行参数（方案 C）
--
-- 背景：以前所有沙盒参数（行动间隔、昼夜、提示词开关、文风补充、系统服务商/模型、
--       地图宽度、集市/委托/纪闻的间隔…）都存在全局 sys_config 里，多个世界共用一套，
--       既不能给修仙世界单独换模型，也不能单独调某个世界的节奏。
--
-- 做法：给 sandbox_world 加一列 settings_json，存**这个世界自己的**沙盒参数：
--   · 读取顺序：世界配置 → 全局 sys_config → 代码默认值；
--     所以列为空的老世界行为完全不变，新建世界也天然继承一套可用的默认值；
--   · 后台在「世界与地图 → 设置」里保存时，写进的就是当前选中世界的这一列；
--   · 唯一例外是「AI 调用总闸」sandbox_enabled：它始终是全局的，用来一键停掉所有世界
--     （单个世界的启停用世界列表里的「启用 / 停用」）。
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

CALL `bcblog_add_col`('sandbox_world', 'settings_json',
    'TEXT NULL COMMENT ''这个世界的沙盒参数（JSON）；为空表示全部沿用全局默认''');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 自检：每个世界是否都有自己的参数列（刚升级完都是 NULL，第一次在后台保存后才有内容）
SELECT `id`, `name`, `enabled`, `portal_visible`,
       IF(`settings_json` IS NULL OR `settings_json` = '', '跟随全局默认', '已单独配置') AS `参数状态`
FROM `sandbox_world` ORDER BY `id`;
