-- ============================================================================
-- 沙盒世界：互动触发「回应回合」
-- ----------------------------------------------------------------------------
-- 背景：角色 A 行动时如果和其他角色 B 产生了互动，而 B 还没到行动时间，
--       就会出现「A 已经搭话、B 还不知道」的信息不对等。
--
-- 本次改动：
--   1. 角色行动产生了互动（companions 非空）时，被互动的角色会立即行动一次（回应回合）。
--   2. 为防止无限循环，增加了 4 层防护：
--      - 回应链深度上限（默认 1：只回应一轮，回应方的行动不再继续触发新的回应）
--      - 每轮最多触发的回应次数（默认 3）
--      - 冷却时间：刚行动过的角色不做立即回应（默认 15 分钟内不重复触发）
--      - 本轮已经排队的角色不再额外触发（避免同一角色连着行动两次）
--   3. sandbox_act 增加 reaction 字段，标记这条记录是「回应回合」，后台可区分。
--   4. 脚本可重复执行。
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

CALL bcblog_add_col('sandbox_act', 'reaction',
    'tinyint NOT NULL DEFAULT 0 COMMENT ''是否由其他角色的互动触发的回应回合：1 是'' AFTER `manual`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_chain_max_depth', '1', '互动回应链最大深度：0 关闭立即回应，1 只回应一轮，2 允许对方再回应一次'),
    ('sandbox_chain_limit_per_round', '3', '每轮调度最多触发的回应次数，防止一次性消耗过多 token'),
    ('sandbox_reaction_cooldown_minutes', '15', '刚行动过的角色在该时间内不做立即回应，避免连着说话；0 表示不限制');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'reaction';
