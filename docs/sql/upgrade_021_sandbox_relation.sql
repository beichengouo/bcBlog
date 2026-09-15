-- ============================================================================
-- 沙盒世界：角色之间的好感度
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 新增 sandbox_relation 表，记录「A 对 B 的好感度」（有方向），
--      AI 每次行动会返回 favor_changes，服务端按角色名累加。
--   2. sandbox_act 增加 favor_change 字段，记录这一步的好感度变化，前台时间线展示。
--   3. 好感度范围 -100 ~ 100，前台按区间显示为 敌视 / 反感 / 陌生 / 相识 / 友好 / 亲近 / 挚友。
--   4. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_relation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID（好感度的持有方）',
    `target_id` bigint NOT NULL COMMENT '对象角色ID',
    `favor` int NOT NULL DEFAULT 0 COMMENT '好感度 -100~100',
    `remark` varchar(200) DEFAULT NULL COMMENT '管理员备注，例如「在集市认识的酒友」',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`character_id`, `target_id`),
    KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色好感度';

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

CALL bcblog_add_col('sandbox_act', 'favor_change',
    'varchar(200) DEFAULT NULL COMMENT ''这一步的好感度变化，如「零 +3」'' AFTER `companions`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 自检
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_relation';

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'favor_change';
