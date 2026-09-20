-- ============================================================
-- 064 沙盒：装备「按字段」而不是「按名字」
--
-- 063 里物品进背包时是按名字猜槽位（「匕首」「盾」「甲」…），当时是为了兜住历史数据。
-- 但这样等于用名字限制 AI 的发挥：想给一把剑取名叫「青光」就认不出来了。
-- 现在改成**字段决定**：
--   · 集市商品：新增 slot / power_bonus 两列，AI 生成商品时直接给这两个字段，
--     买下/赠送进背包时原样带过去；
--   · 委托奖励：reward_items 是 JSON，每件奖励可以带 slot / power_bonus（无需改表）；
--   · 角色自己行动得到的物品：items_change 里同样支持 slot / bonus（063 已支持）。
-- 名字只作为**兜底**：字段缺失时（历史数据、AI 忘了写）才按名字猜一次。
--
-- 脚本幂等，可重复执行。
-- ============================================================

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

CALL bcblog_add_col('sandbox_shop_item', 'slot',
    'varchar(16) NOT NULL DEFAULT ''none'' COMMENT ''装备槽位：none 非装备 / weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品'' AFTER `rarity`');
CALL bcblog_add_col('sandbox_shop_item', 'power_bonus',
    'int NOT NULL DEFAULT 0 COMMENT ''装备加成（战斗力）；0 表示不是装备'' AFTER `slot`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_shop_item'
  AND COLUMN_NAME IN ('slot', 'power_bonus');

-- ------------------------------------------------------------
-- 一次性回填：现有这批商品是 064 之前生成的，没有 slot 字段。
-- 不回填的话，玩家现在买到的「黑潮影刃匕首」会被当成普通杂物（字段说不是装备）。
-- 所以这里按名字给**存量商品**补一次槽位（等价于程序里的兜底规则），
-- 之后新生成的商品一律以 AI 给的字段为准。加成取该品质区间的中点。
-- ------------------------------------------------------------
-- 先纠正一类误判：名字里带「剑 / 甲」但其实是资料或材料的东西
-- （「风羽剑技残页」「冰龙残鳞」这类），它们必须保持"不是装备"。
-- 这一段同时兼顾"重复执行"：万一之前跑过一次把某个填错了，这里会把它拨回来。
-- ------------------------------------------------------------
UPDATE `sandbox_shop_item`
SET `slot` = 'none', `power_bonus` = 0
WHERE `name` REGEXP '残页|残卷|书页|图纸|图样|笔记|手稿|拓本|拓片|配方|药方|晶石|符文石|鳞片|碎片|残片|粉尘|粉末|木料|石料|皮毛|兽皮|碎石|齿轮|残骸';

-- ------------------------------------------------------------
UPDATE `sandbox_shop_item`
SET `slot` = CASE
        WHEN `name` REGEXP '剑|刀|匕首|短刃|弓|弩|法杖|魔杖|权杖|锤|斧|镰|长枪|矛' THEN 'weapon'
        WHEN `name` REGEXP '盾|副手|法典|魔典|法器|灯笼|提灯' THEN 'offhand'
        WHEN `name` REGEXP '甲|铠|袍|斗篷|披风|外衣|护腕|头盔|兜帽|靴|护腿|胸针' THEN 'armor'
        WHEN `name` REGEXP '戒指|指环|项链|吊坠|护符|耳环|手镯|徽章|勋章|腰带|香囊|药囊' THEN 'accessory'
        ELSE 'none'
    END
WHERE `slot` = 'none'
  AND `name` NOT REGEXP '磨刀石|除锈膏|药水|药剂|药膏|草药|药草|干粮|面包|饼干|烤肉|热汤|麦酒|果酒|浆果|蘑菇|种子|羽毛|矿石|木材|绳子|火把|蜡烛|卷轴|纸张|信件|地图|钥匙|钱包|饲料|鱼饵|骨哨|干酪|腌肉|蜜饯|绷带|香薰|残页|残卷|书页|图纸|图样|笔记|手稿|拓本|拓片|配方|药方|晶石|符文石|鳞片|碎片|残片|粉尘|粉末|木料|石料|皮毛|兽皮|碎石|齿轮|残骸';

UPDATE `sandbox_shop_item`
SET `power_bonus` = CASE `rarity`
        WHEN 1 THEN 5 WHEN 2 THEN 15 WHEN 3 THEN 25 WHEN 4 THEN 45 ELSE 90 END
WHERE `slot` <> 'none' AND `power_bonus` = 0;

SELECT `slot`, COUNT(*) AS 件数 FROM `sandbox_shop_item` WHERE `enabled` = 1 GROUP BY `slot`;
SELECT `name`, `rarity`, `slot`, `power_bonus` FROM `sandbox_shop_item`
WHERE `enabled` = 1 AND `slot` <> 'none' ORDER BY `slot`, `id` LIMIT 20;
