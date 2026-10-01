-- ============================================================
-- 072 网站AI「IRIS」：写作计划（方案 D）
--
-- 背景：以前写文章是"每天一个固定时间点、自动只写一篇"，后台把每天篇数调到 5 也没用。
--   现在改成：篇数由后台「每天最多几篇」决定，时间由服务端在「写作窗口」内排布
--   （随机 或 均匀），当天计划在首次执行时生成，并在后台概览里可见。
--
-- 本次新增 4 列（表已存在，使用幂等的加列过程）：
--   article_window_start  写作窗口开始（默认 09:00）
--   article_window_end    写作窗口结束（默认 22:00）
--   article_random        1 随机（更像真人，默认）/ 0 均匀（更好预期）
--   article_plan_json     当天写作计划 JSON（各时段与状态）
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

CALL `bcblog_add_col`('site_ai_profile', 'article_window_start',
    'varchar(5) NOT NULL DEFAULT ''09:00'' COMMENT ''写作窗口开始时间''');
CALL `bcblog_add_col`('site_ai_profile', 'article_window_end',
    'varchar(5) NOT NULL DEFAULT ''22:00'' COMMENT ''写作窗口结束时间''');
CALL `bcblog_add_col`('site_ai_profile', 'article_random',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''写作时间是否随机分布：1 随机 / 0 均匀''');
CALL `bcblog_add_col`('site_ai_profile', 'article_plan_json',
    'text COMMENT ''当天写作计划 JSON（日期 + 各时段与状态）''');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 自检：4 列是否都到位
SELECT '写作计划相关列（期望 4）' AS item, COUNT(*) AS value
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_ai_profile'
  AND COLUMN_NAME IN ('article_window_start', 'article_window_end', 'article_random', 'article_plan_json');
