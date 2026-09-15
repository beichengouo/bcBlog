-- ============================================================================
-- 沙盒世界：每日记忆总结 + 角色背包
-- ----------------------------------------------------------------------------
-- 1. sandbox_memory：每天晚上把角色当天的行动总结成一段长期记忆，
--    后续几天的提示词只带记忆 + 近期行动，避免历史日志越堆越多。
-- 2. sandbox_item：角色背包（同角色同物品唯一，数量累加，用完自动移除）。
-- 3. sandbox_act.item_change：记录这一步的物品变化，前台时间线展示。
-- 4. 保留天数接入现有「系统设置 → 数据清理」：行动日志默认 7 天、记忆默认 30 天。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_memory` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `memory_date` date NOT NULL COMMENT '记忆对应的日期',
    `summary` text COMMENT '当天的记忆总结',
    `act_count` int NOT NULL DEFAULT 0 COMMENT '当天行动条数',
    `from_ai` tinyint NOT NULL DEFAULT 1 COMMENT '是否由 AI 总结：1 是，0 为兜底拼接',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_char_date` (`character_id`, `memory_date`),
    KEY `idx_memory_date` (`memory_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色每日记忆';

CREATE TABLE IF NOT EXISTS `sandbox_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `name` varchar(100) NOT NULL COMMENT '物品名称',
    `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
    `description` varchar(300) DEFAULT NULL COMMENT '物品说明（管理员可补充）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_char_item` (`character_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色背包';

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

CALL bcblog_add_col('sandbox_act', 'item_change',
    'varchar(300) DEFAULT NULL COMMENT ''这一步的物品变化，如「获得 干粮 +1」'' AFTER `favor_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 记忆相关配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_memory_enabled', '1', '是否开启每日记忆总结：1 开启，0 关闭'),
    ('sandbox_memory_time', '23:50', '每天生成记忆总结的时间 HH:mm（服务器时间）'),
    ('sandbox_memory_prompt_days', '5', '提示词里携带最近几天的记忆总结'),
    ('sandbox_memory_delete_acts', '0', '总结后是否立即删除当天行动日志：1 是（不推荐，前台时间线会看不到当天内容），0 否');

-- 保留天数：接入现有数据清理
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_sandbox_act_days', '7', 'sandbox_act 沙盒行动日志保留天数'),
    ('cleanup_sandbox_memory_days', '30', 'sandbox_memory 沙盒记忆保留天数');

-- 自检
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('sandbox_memory', 'sandbox_item')
ORDER BY TABLE_NAME;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'item_change';
