-- ============================================================================
-- 沙盒世界：多角色同时行动与角色互动
-- ----------------------------------------------------------------------------
-- 说明：
--   1. sandbox_act 增加 companions 字段：记录这一步和哪些角色发生了互动，
--      由 AI 按提示词返回，前台时间线会显示「与 XX 互动」。
--   2. 新增配置 sandbox_batch_window_minutes：调度时把「已到期或即将到期」的角色
--      合并成同一轮一起行动，方便角色之间互相遇见（0 表示只处理已到期的角色）。
--   3. 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_act', 'companions',
    'varchar(200) DEFAULT NULL COMMENT ''这一步互动的其他角色，逗号分隔'' AFTER `inner_voice`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_batch_window_minutes', '5', '同一轮行动的时间窗（分钟）：已到期或即将到期的角色会一起行动，方便互相遇见；0 表示只处理已到期的角色');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'companions';
