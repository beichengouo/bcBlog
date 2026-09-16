-- ============================================================================
-- 沙盒：AI 调用失败后的退避（避免每 5 分钟无脑重试烧额度）
-- ----------------------------------------------------------------------------
-- 背景：沙盒的每 5 分钟扫描会把「已到期」的角色拿去执行。此前 AI 调用失败时
--       只记录 last_error、不动 next_run_time，角色会一直保持「逾期」状态，
--       于是每 5 分钟重试一次；而失败不产生行动记录，所以也绕过了「每日上限」，
--       模型一旦挂掉就会持续消耗额度。
--
-- 本次改动：
--   1. sandbox_character 新增 fail_count：记录连续失败次数，成功后清零；
--   2. 失败时按「起步值 × 2^(次数-1)」退避，超过上限就取上限，再把 next_run_time
--      往后推，让角色不再处于「逾期」状态；管理员手动「立即执行一次」不受影响。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_036_sandbox_fail_backoff.sql
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

-- 连续失败次数：成功一次就清零
CALL bcblog_add_col('sandbox_character', 'fail_count',
    'int NOT NULL DEFAULT 0 COMMENT ''连续失败次数：AI 调用连续失败时累加，成功后清零'' AFTER `last_error`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 退避参数（后台「沙盒世界 → 运行参数」里可改）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_fail_backoff_base_minutes', '15', '沙盒 AI 调用失败后的退避起步分钟数（连续失败按 2 倍递增）'),
    ('sandbox_fail_backoff_max_minutes', '120', '沙盒 AI 调用失败退避的上限分钟数');

-- 自检
SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'fail_count';

SELECT config_key AS '配置项', config_value AS '当前值', remark AS '说明'
FROM `sys_config`
WHERE config_key IN ('sandbox_fail_backoff_base_minutes', 'sandbox_fail_backoff_max_minutes');
