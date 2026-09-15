-- ============================================================================
-- 沙盒世界：角色金币与金币流水
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 角色新增 coins 字段（金币），AI 日常活动赚取/消耗，前台用户可用积分贡献。
--   2. 行动记录新增 coin_change，前台时间线展示本次金币变化。
--   3. 新增 sandbox_coin_log 金币流水（贡献 / 赚取 / 消耗 / 管理员调整）。
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

-- 角色金币余额
CALL bcblog_add_col('sandbox_character', 'coins',
    'int NOT NULL DEFAULT 0 COMMENT ''金币余额'' AFTER `status_json`');

-- 每次行动的金币变化，便于前台时间线展示
CALL bcblog_add_col('sandbox_act', 'coin_change',
    'int NOT NULL DEFAULT 0 COMMENT ''本次金币变化，正为赚取、负为消耗'' AFTER `status_json`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 金币流水
CREATE TABLE IF NOT EXISTS `sandbox_coin_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `user_id` bigint DEFAULT NULL COMMENT '贡献人（赚取/消耗为空）',
    `user_name` varchar(100) DEFAULT NULL COMMENT '贡献人昵称快照',
    `type` varchar(20) NOT NULL COMMENT '类型：contribute 贡献 / earn 赚取 / spend 消耗 / admin 管理员调整',
    `coins` int NOT NULL COMMENT '金币变化，正为增加、负为减少',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '贡献消耗的积分',
    `balance` int NOT NULL DEFAULT 0 COMMENT '变化后余额',
    `remark` varchar(200) DEFAULT NULL COMMENT '说明',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character` (`character_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒金币流水';

-- 金币相关配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_coin_rate', '10', '每 1 积分可兑换的金币数量'),
    ('sandbox_coin_max_points', '100', '单次贡献积分上限，0 表示不限制');

-- 自检
SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'coins')
    OR (TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'coin_change'))
ORDER BY TABLE_NAME;
