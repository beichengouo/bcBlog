-- ============================================================================
-- 沙盒世界：旅人纪闻自动生成 + 行动记录标记引用的纪闻
-- ----------------------------------------------------------------------------
-- 1. sandbox_act 增加 news_ref：记录这一步参考/听说了哪几条旅人纪闻。
-- 2. 新增配置：是否每天定时自动生成纪闻、自动生成时间（默认 07:00）。
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

CALL bcblog_add_col('sandbox_act', 'news_ref',
    'varchar(300) DEFAULT NULL COMMENT ''这一步参考/听说的旅人纪闻标题，顿号分隔'' AFTER `next_after_reason`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_news_auto_enabled', '1', '是否每天定时自动生成旅人纪闻：1 开启，0 关闭'),
    ('sandbox_news_auto_time', '07:00', '自动生成旅人纪闻的时间 HH:mm（服务器时间）');

SELECT config_key, config_value FROM `sys_config`
WHERE config_key LIKE 'sandbox_news_auto%'
ORDER BY config_key;
