-- ============================================================
-- 054 沙盒：地点战力区间 + 每步运气
--
-- 一、地点战力区间（power_min / power_max）
--   以前遭遇的对手强度是按"角色战斗力 × 0.55~1.6 倍"算的，结果是同一个魔物森林里，
--   敌人会跟着角色一起变强（你 16 战力时遇到战力 20 的豹子，练到 40 就冒出战力 50 的蛛母），
--   世界像在陪着角色演戏。改成地点自带强度区间后：
--     · 同一个地方的强度是稳定的，只有"这次碰上弱的还是强的"在变；
--     · 角色变强会真的被感知到（以前只能逃的地方，后来能应付）；
--     · 低战力角色误闯危险地区会真的九死一生——这正是危险地区该有的样子。
--   默认值按危险度回填：危险度 1 → 6~14，危险度 2 → 15~30，危险度 3 → 25~55（管理员可改）。
--
-- 二、每步运气（sandbox_act.luck）
--   运气**不改变"会不会遭遇"**（那由地点危险度掷骰决定，属于世界的客观事实），
--   它只影响"遭遇之后怎么发展"，以及日常里的偶然小事：
--     · 运气好：恰好闪过、对手失手、有人路过搭救、大雨浇灭了火、路上捡到东西、商队多给工钱……
--     · 运气差：后背挨一爪、装备被扯坏、被扒手盯上、淋雨生病、谈好的活儿被人抢走……
--   **运气不能推翻实力差距，也不会让角色死**（见规则 23 与 25）。
--   取值为 -3（大凶）~ +3（大吉）：当天首次行动时抽一个"当天基调"（默认 ±2），
--   之后每步在基调上抖动（默认 ±1），这样才有"今天诸事不顺 / 今天顺得很"的连贯感。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_luck_enabled', 'on', '沙盒每步运气开关：on 开启（默认）/ off 关闭，关闭后提示词里不给运气值'),
    ('sandbox_luck_day_range', '2', '每天运气基调的幅度：当天首步在 -N ~ +N 之间抽一个基调（默认 2）'),
    ('sandbox_luck_step_jitter', '1', '每步在当天基调上抖动的幅度（默认 ±1），0 表示当天运气固定不变');

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
CALL bcblog_add_col('sandbox_location', 'power_min',
    'int DEFAULT NULL COMMENT ''该地点生物的战斗力下限（遭遇对手强度落在这个区间内）'' AFTER `danger_level`');
CALL bcblog_add_col('sandbox_location', 'power_max',
    'int DEFAULT NULL COMMENT ''该地点生物的战斗力上限'' AFTER `power_min`');
CALL bcblog_add_col('sandbox_act', 'luck',
    'int DEFAULT NULL COMMENT ''本步运气值 -3 大凶 ~ +3 大吉（服务端生成，前台展示）'' AFTER `encounter`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 按危险度回填战力区间（只填没配过的地点）
UPDATE `sandbox_location` SET `power_min` = 6,  `power_max` = 14 WHERE `danger_level` = 1 AND `power_min` IS NULL;
UPDATE `sandbox_location` SET `power_min` = 15, `power_max` = 30 WHERE `danger_level` = 2 AND `power_min` IS NULL;
UPDATE `sandbox_location` SET `power_min` = 25, `power_max` = 55 WHERE `danger_level` = 3 AND `power_min` IS NULL;
