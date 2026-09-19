-- ============================================================
-- 053 沙盒「本步遭遇」：让危险地区的危险真的会发生
--
-- 背景（实测）：危险度=3 的魔物森林里，一个采药人跑了 8 步只有 1 次轻遭遇，
--   全程没受伤。原因是"要不要出事"完全交给 AI 决定，而 AI 有两个倾向：
--     · 规则里写着"不要每一步都出事"，它就严格按"偶尔"来；
--     · 角色是理性的，战力低、目标只是采药，就一路写"警觉、避开、撤离"。
--   光在地点描述里写"有 XX 魔物"也不管用——AI 照样可以写"我绕开了它们"。
--
-- 现在的做法：**由服务端掷骰子决定"这一步遇上了什么"**，把具体的遭遇直接写进提示词，
-- AI 只负责应对（迎战 / 逃跑 / 躲藏 / 丢东西脱身），不能当作没看见。
--
--   触发条件：地点危险度 ≥ 2、角色不是濒死、当天遭遇次数未超上限；
--   概率：危险度 2 用 sandbox_encounter_chance_2，危险度 3 用 sandbox_encounter_chance_3（百分比）；
--   对手强度：以角色战斗力为基准随机（0.55~1.6 倍），并在提示词里说明"比你强 / 差不多 / 比你弱"；
--   对手是什么：由 AI 按该地点与世界观自行发挥（野兽、魔物、劫匪、守卫、天灾皆可），但强度必须符合给定数字。
--
-- 新增字段 sandbox_act.encounter：记录这一步注入的遭遇，方便后台与前台上看到"他到底遇上了什么"，
-- 也用于统计当日遭遇次数（防止一天被反复袭击）。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_encounter_enabled', 'on', '沙盒「本步遭遇」总开关：on 开启（默认）/ off 关闭，关闭后危险地区不再由服务端掷骰子注入遭遇'),
    ('sandbox_encounter_chance_2', '30', '危险度为「较高」的地点，每步触发遭遇的概率（百分比，默认 30）'),
    ('sandbox_encounter_chance_3', '55', '危险度为「危险」的地点，每步触发遭遇的概率（百分比，默认 55）'),
    ('sandbox_encounter_daily_limit', '3', '同一角色每天最多被注入几次遭遇（默认 3，0 表示不限制），避免一天被反复袭击');

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
CALL bcblog_add_col('sandbox_act', 'encounter',
    'varchar(300) DEFAULT NULL COMMENT ''本步由服务端注入的遭遇（危险地区掷骰命中时才会有）'' AFTER `news_ref`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
