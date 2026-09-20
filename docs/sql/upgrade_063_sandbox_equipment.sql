-- ============================================================
-- 063 沙盒：装备栏
--
-- 以前角色的战斗力只有一个数字（combat_power），背包物品也只是一串名字，
-- 没有"这是武器还是药水""这件装备加多少战力"的概念。现在拆成两块：
--
--   自身实力 combat_power  —— AI 靠 combat_change 慢慢练上去的底子（学会新魔法、受伤变弱…）
--   装备加成 equip_power   —— 装备栏里那几件装备的加成合计（服务端按装备实时重算）
--   有效战斗力 = 自身实力 + 装备加成（提示词、遭遇判定、前台显示都用这个）
--
-- 装备栏 4 格：weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品。
-- 装备的加成数值由 AI 给，服务端按品质区间夹取（默认见下面的配置）：
--   普通 +1~10 / 精良 +10~20 / 稀有 +20~30 / 史诗 +30~60 / 传说 +60~120
-- 另有硬规则：**装备加成不能超过角色自身实力**（拿着远超自己的神兵是用不动的），
--   违反时这一步的装备无效，并让 AI 二次自检改写。
--
-- 破损走"事件式"：AI 明确写某件装备破损 → 服务端把它卸下、加「破损的」前缀、加成清零
-- （暂时不做耐久条与修理，二期再说）。
--
-- 存量角色：现在的 combat_power 一律当作"裸装实力"，背包里已有的武器**不会**自动装上。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_equip_enabled', '1',
     '沙盒装备栏开关：1 开启（默认）/ 0 关闭，关闭后提示词里不给装备规则、AI 的装备动作一律忽略'),
    ('sandbox_equip_bonus_by_rarity', '1-10,10-20,20-30,30-60,60-120',
     '装备加成按品质夹取的区间（战斗力），依次对应品质 1~5：普通 / 精良 / 稀有 / 史诗 / 传说。格式 下限-上限，逗号分隔');

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

CALL bcblog_add_col('sandbox_item', 'slot',
    'varchar(16) NOT NULL DEFAULT ''none'' COMMENT ''装备槽位：none 非装备 / weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品'' AFTER `rarity`');
CALL bcblog_add_col('sandbox_item', 'power_bonus',
    'int NOT NULL DEFAULT 0 COMMENT ''装备加成（战斗力）；0 表示不是装备或没有加成'' AFTER `slot`');
CALL bcblog_add_col('sandbox_item', 'equipped',
    'tinyint NOT NULL DEFAULT 0 COMMENT ''1 = 已装备在角色装备栏里'' AFTER `power_bonus`');
CALL bcblog_add_col('sandbox_item', 'broken',
    'tinyint NOT NULL DEFAULT 0 COMMENT ''1 = 已破损（不能再装备，加成按 0 算）'' AFTER `equipped`');
CALL bcblog_add_col('sandbox_character', 'equip_power',
    'int NOT NULL DEFAULT 0 COMMENT ''装备加成合计（冗余列，装备变更时按装备栏重算）'' AFTER `combat_power`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_equip_enabled', 'sandbox_equip_bonus_by_rarity');

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_item'
  AND COLUMN_NAME IN ('slot', 'power_bonus', 'equipped', 'broken');
