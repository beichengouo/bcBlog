-- ============================================================
-- 050 沙盒「遭遇与战斗」+ 伤势档位 + 绝对不死保底
--
-- 背景：过去的提示词第 9 条写着「整体风格温和、日常，避免暴力」，结果角色在魔物森林里
--   也像逛公园一样，战斗力这个字段几乎没有存在感，跑到危险的荒野也从不出事。
--
-- 现在改成「真实但可控」：
--   1. 地点新增 danger_level（危险度）：0 安全 / 1 较低 / 2 较高 / 3 危险，
--      管理员在【沙盒世界 → 世界与地图】里维护；危险度会写进行动提示词，
--      AI 据此判断这一步会不会遭遇野兽、魔物、劫匪或自然灾害；
--   2. 角色状态里新增固定项「伤势」：无恙 / 轻伤 / 重伤 / 濒死；
--   3. **绝对不死**：AI 提示词里写死规则（最坏只能到濒死），服务端还有两道硬兜底——
--      · 死亡关键词命中时，把原文回传给 AI 做一次"只改死亡表述"的改写；
--      · 改写后仍不干净（或改写失败）时，服务端直接把死亡词替换成濒死/昏迷表述；
--      · 「伤势」字段做白名单归一，写了「死亡」这类值会被强制归一成「濒死」；
--   4. 濒死 / 重伤之后自动拉长下一次行动间隔，避免刚濒死一小时后又活蹦乱跳。
--
-- 危险度的初始值：脚本按名字给一个建议值（魔物/荒野=3，森林/遗迹/山脉/部落=2，其余=1），
-- 只更新仍是默认值 1 的行，管理员改过的不动。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_injury_dying_minutes', '360', '沙盒角色处于「濒死」时，下一次行动的最小间隔（分钟）：默认 360（6 小时），避免刚濒死又去冒险'),
    ('sandbox_injury_heavy_minutes', '180', '沙盒角色处于「重伤」时，下一次行动的最小间隔（分钟）：默认 180（3 小时）');

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
CALL bcblog_add_col('sandbox_location', 'danger_level',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''危险度：0 安全 / 1 较低 / 2 较高 / 3 危险（影响 AI 是否遭遇战斗）'' AFTER `description`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 初始建议值：只在管理员还没改过（仍是默认 1）时生效
UPDATE `sandbox_location` SET `danger_level` = 3
WHERE `danger_level` = 1 AND (`name` LIKE '%魔物%' OR `name` LIKE '%荒野%');
UPDATE `sandbox_location` SET `danger_level` = 2
WHERE `danger_level` = 1 AND (`name` LIKE '%森林%' OR `name` LIKE '%遗迹%' OR `name` LIKE '%天空之城%'
       OR `name` LIKE '%山脉%' OR `name` LIKE '%部落%');
